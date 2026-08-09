package com.hamfilm.app.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.OpenableColumns
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.hamfilm.app.R
import com.hamfilm.app.data.ApiConfig
import com.hamfilm.app.data.TokenStore
import com.hamfilm.app.data.ws.*
import com.hamfilm.app.ui.components.*
import com.hamfilm.app.ui.navigation.Routes
import com.hamfilm.app.ui.theme.*
import com.hamfilm.app.viewmodel.RoomViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Locale

private val Emojis = listOf("❤️", "😂", "😮", "👍", "😢", "🔥", "🎬", "🍿", "😘", "🙏")
private val Speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

// رنگ آبی اتاق (هماهنگ با سایت)
private val RoomBlueBg = Color(0xFF0A1222)
private val RoomBlueBar = Brush.horizontalGradient(listOf(Color(0xFF0D2A55), Color(0xFF0E1B45), Color(0xFF0A1230)))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(
    nav: NavHostController,
    roomCode: String,
    initialPassword: String,
    initialVideoUrl: String
) {
    val vm: RoomViewModel = viewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

    // ---------- ExoPlayer ----------
    val player = remember {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_OFF }
    }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    DisposableEffect(player) { onDispose { player.release() } }

    // ---------- اتصال ----------
    var connected by remember { mutableStateOf(false) }
    LaunchedEffect(roomCode) {
        if (!connected) {
            connected = true
            vm.connect(
                code = roomCode,
                name = TokenStore.name.ifBlank { "مهمان" },
                avatar = TokenStore.avatar,
                password = initialPassword,
                initialVideoUrl = initialVideoUrl
            )
            // داده زنده پلیر برای پینگ همگام‌سازی — تیکر روی ترد اصلی (مهم: فقط از اینجا پلیر خوانده می‌شود)
            vm.updatePingData(
                playing = player.playWhenReady,
                positionMs = player.currentPosition,
                buffering = player.playWhenReady && !player.isPlaying
            )
        }
    }

    // تیکر پینگ: هر ۱۵ ثانیه موقعیت واقعی پلیر را روی ترد اصلی می‌خواند و به سوکت می‌دهد.
    // (حلقه پینگ سوکت روی ترد IO است و هرگز پلیر را لمس نمی‌کند → بدون کرش)
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(15_000)
            try {
                vm.updatePingData(
                    playing = player.playWhenReady,
                    positionMs = player.currentPosition,
                    buffering = player.playWhenReady && !player.isPlaying
                )
            } catch (_: Exception) { }
        }
    }

    // پخش ویدیوی اولیه
    LaunchedEffect(initialVideoUrl) {
        if (initialVideoUrl.isNotBlank()) {
            player.setMediaItem(MediaItem.fromUri(initialVideoUrl))
            player.prepare()
            player.playWhenReady = true
        }
    }

    // ── ردگیر آخرین state اعمال‌شده (برای تشخیص پیام واقعاً جدید از echo) ──
    var lastStateAt by remember { mutableStateOf(0L) }
    var lastStateTime by remember { mutableStateOf(0L) }
    var lastStateWasPlaying by remember { mutableStateOf(false) }
    var lastStateSpeed by remember { mutableStateOf(1.0) }

    // کنترل راه دور → پلیر (وضعیت همگام از سرور) — همه در try/catch تا هیچ پیامی کرش نکند
    vm.onRemoteState = { playing, timeSec, speed ->
        try {
            player.playWhenReady = playing
            player.setPlaybackSpeed(speed.toFloat().coerceIn(0.25f, 4f))
            val target = (timeSec * 1000).toLong()
            val now = System.currentTimeMillis()
            // موقعیت پیش‌بینی‌شده بر اساس آخرین state — اگر پیام جدیدی نبود، سیک نمی‌کنیم
            val expected = if (lastStateAt > 0L && lastStateWasPlaying) {
                lastStateTime + ((now - lastStateAt) * lastStateSpeed).toLong()
            } else lastStateTime
            lastStateAt = now
            lastStateTime = target
            lastStateWasPlaying = playing
            lastStateSpeed = speed
            // فقط وقتی سرور واقعاً جابه‌جا شده (seek یا اختلاف واقعی) → سیک
            // آستانه ۹۰۰ms — سینک دقیق بدون لرزش
            if (target > 0 && kotlin.math.abs(target - expected) > 900 &&
                kotlin.math.abs(player.currentPosition - target) > 900
            ) {
                player.seekTo(target)
            }
        } catch (_: Exception) { }
    }
    vm.onRemoteCorrect = { timeSec, playing ->
        try {
            val target = (timeSec * 1000).toLong()
            // دفاع: اگر سرور state معتبری ندارد (time=0 و paused — اتاق تازه) و ما در حال پخش هستیم،
            // این اصلاح را نادیده بگیر — در غیر این صورت فیلم بی‌دلیل استپ می‌شود
            val bogusState = timeSec <= 0.0 && !playing && player.playWhenReady && player.currentPosition > 5000
            if (!bogusState) {
                if (target > 0 && kotlin.math.abs(player.currentPosition - target) > 1200) {
                    player.seekTo(target)
                }
                player.playWhenReady = playing
            }
        } catch (_: Exception) { }
    }
    vm.onRemoteVideo = { url ->
        try {
            if (url.isNotBlank()) {
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.playWhenReady = true
            }
        } catch (_: Exception) { }
    }

    // ---------- تمام‌صفحه ----------
    var fullscreen by remember { mutableStateOf(false) }
    val activity = context as? Activity
    DisposableEffect(fullscreen) {
        activity?.window?.let { w ->
            val ctrl = WindowInsetsControllerCompat(w, w.decorView)
            if (fullscreen) {
                ctrl.hide(WindowInsetsCompat.Type.systemBars())
                ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                ctrl.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            activity?.window?.let { w ->
                WindowInsetsControllerCompat(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // ---------- پیکر فایل محلی ----------
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val name = context.queryDisplayName(it)
            val size = context.querySize(it)
            // فقط آماده‌سازی — بدون پخش خودکار (کاربر خودش دکمه پخش را می‌زند)
            player.setMediaItem(MediaItem.fromUri(it))
            player.prepare()
            player.playWhenReady = false
            player.seekTo(0)
            // hash محتوای فایل (برای همگام‌سازی) — در پس‌زمینه تا UI لگ نزند
            val hash = context.queryFileHash(it)
            vm.shareLocalFile(name, size, hash)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun pickLocalFile() {
        filePicker.launch(arrayOf("video/*", "audio/*"))
    }

    // state تنظیمات پلیر
    var playerSettingsOpen by remember { mutableStateOf(false) }
    var subtitleUri by remember { mutableStateOf<Uri?>(null) }
    var subtitleEnabled by remember { mutableStateOf(false) }
    var subtitleColorIndex by remember { mutableStateOf(0) }

    // انتخاب فایل زیرنویس
    val subtitlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            subtitleUri = it
            subtitleEnabled = true
            // اتصال زیرنویس به پلیر
            player.setMediaItem(
                player.currentMediaItem!!.buildUpon()
                    .setSubtitleConfigurations(
                        listOf(
                            androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.toString()))
                                .setMimeType("text/subrip")
                                .setLanguage("fa")
                                .build()
                        )
                    )
                    .build()
            )
            player.prepare()
            if (player.playWhenReady) player.play()
        }
    }

    fun pickSubtitle() {
        subtitlePicker.launch(arrayOf("*/*"))
    }

    // ---------- state ها ----------
    var chatText by remember { mutableStateOf("") }
    var chatOpen by remember { mutableStateOf(false) }
    var fsChatOpen by remember { mutableStateOf(false) }
    val unread by vm.unread.collectAsState()

    // خروج با دکمه بازگشت فیزیکی — هوشمند: اول چت/تمام‌صفحه بسته می‌شود، بعد تأیید خروج
    var leaveConfirmOpen by remember { mutableStateOf(false) }
    BackHandler {
        when {
            fsChatOpen -> fsChatOpen = false
            fullscreen -> fullscreen = false
            chatOpen && !isLandscape -> chatOpen = false
            else -> leaveConfirmOpen = true
        }
    }

    // ── auto-hide کنترل‌ها در تمام‌صفحه: بعد از ۳ ثانیه بدون لمس مخفی می‌شوند ──
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(fullscreen, controlsVisible) {
        if (fullscreen && controlsVisible) {
            delay(3000)
            controlsVisible = false
        }
    }
    // با ورود به تمام‌صفحه، کنترل‌ها حتماً نمایش داده می‌شوند (حتی اگر قبلاً مخفی شده بودند)
    LaunchedEffect(fullscreen) {
        if (fullscreen) controlsVisible = true
    }
    var membersOpen by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    var urlDialogOpen by remember { mutableStateOf(false) }
    var guestDialogOpen by remember { mutableStateOf(false) }
    var helpDialogOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var typingJob by remember { mutableStateOf<Job?>(null) }

    // وضعیت زنده پلیر برای UI (زمان/مدت)
    var playerPosMs by remember { mutableStateOf(0L) }
    var playerDurMs by remember { mutableStateOf(0L) }

    val peers by vm.peers.collectAsState()
    val messages by vm.messages.collectAsState()
    val typing by vm.typing.collectAsState()
    val socketState by vm.socketState.collectAsState()
    val fileInfo by vm.fileInfo.collectAsState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
            vm.markChatRead()
        }
    }

    // وضعیت پخش نمایشی (از playWhenReady — موقع بافرینگ هم درست می‌ماند)
    var playerPlaying by remember { mutableStateOf(player.playWhenReady) }

    // تیکر سبک موقعیت (فقط هنگام پخش — مصرف ناچیز)
    LaunchedEffect(player) {
        while (true) {
            kotlinx.coroutines.delay(500)
            if (player.duration > 0) playerDurMs = player.duration
            if (player.playWhenReady) playerPosMs = player.currentPosition
        }
    }

    // ── سینک نرم: هر ۴ ثانیه موقعیت محلی را با تخمین سرور مقایسه کن ──
    // (اگر کسی عقب/جلو افتاد، بدون نیاز به سرور خودش را اصلاح می‌کند)
    LaunchedEffect(player) {
        while (true) {
            kotlinx.coroutines.delay(4000)
            if (!player.playWhenReady) continue
            val now = System.currentTimeMillis()
            val serverEstimate = if (lastStateAt > 0L && lastStateWasPlaying) {
                lastStateTime + ((now - lastStateAt) * lastStateSpeed).toLong()
            } else 0L
            if (serverEstimate > 0) {
                val drift = player.currentPosition - serverEstimate
                if (kotlin.math.abs(drift) > 1200) {
                    player.seekTo(serverEstimate)
                }
            }
        }
    }

    fun onPlayPause() {
        if (!vm.canControl) {
            android.widget.Toast.makeText(
                context,
                "فقط میزبان اتاق می‌تواند پخش را کنترل کند",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val next = !player.playWhenReady
        player.playWhenReady = next
        vm.setPlayback(next, player.currentPosition)
    }

    fun onSeekBy(deltaMs: Long) {
        if (!vm.canControl) {
            android.widget.Toast.makeText(context, "فقط میزبان اتاق می‌تواند پخش را کنترل کند", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val dur = if (playerDurMs > 0) playerDurMs else player.duration
        val target = (playerPosMs + deltaMs).coerceIn(0L, if (dur > 0) dur else Long.MAX_VALUE)
        player.seekTo(target)
        playerPosMs = target
        vm.seekTo(target)
    }

    fun onSeekTo(ms: Long) {
        if (!vm.canControl) {
            android.widget.Toast.makeText(context, "فقط میزبان اتاق می‌تواند پخش را کنترل کند", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        player.seekTo(ms)
        playerPosMs = ms
        vm.seekTo(ms)
    }

    // ── همگام‌سازی وضعیت پخش پلیر (آیکون درست + اطلاع ViewModel) ──
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                playerPlaying = playWhenReady
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                vm.onLocalPlayStateChange(isPlaying)
            }

            // پایان فیلم: پخش را متوقف کن و اگر میزبان/کنترل‌دار هستی به بقیه هم اعلام کن
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    player.playWhenReady = false
                    if (vm.canControl) {
                        val endMs = if (player.duration > 0) player.duration else player.currentPosition
                        vm.setPlayback(false, endMs)
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // اعمال رنگ زیرنویس
    fun applySubtitleColor(index: Int) {
        subtitleColorIndex = index
        try {
            val playerView = playerViewRef
            if (playerView != null) {
                // زیرنویس داخلی پلیر — فقط استایل آن را عوض می‌کنیم
                playerView.subtitleView?.setStyle(com.hamfilm.app.ui.components.buildCaptionStyle(index))
            }
        } catch (_: Exception) {}
    }

    // ---------- چیدمان ----------
    if (fullscreen) {
        // ═══ حالت تمام‌صفحه: فقط ویدیو + کنترل‌های شناور (auto-hide بعد از ۳ ثانیه) ═══
        // لمس صفحه: نمایش/مخفی‌کردن کنترل‌ها — دکمه خروج همیشه visible
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { controlsVisible = !controlsVisible }
        ) {
            VideoSection(
                player = player, vm = vm, fileInfo = fileInfo,
                isPlaying = playerPlaying,
                roomName = vm.roomName,
                roomCode = roomCode,
                onPlayPause = ::onPlayPause,
                onSeekBy = ::onSeekBy,
                onSeekTo = ::onSeekTo,
                onFullscreen = { fullscreen = false },
                onPlayerViewReady = { playerViewRef = it },
                playerPosMs = playerPosMs,
                playerDurMs = playerDurMs,
                showControls = controlsVisible,
                showRoomName = false,
                isFullscreen = true,
                modifier = Modifier.fillMaxSize()
            )

            // ── پنل چت افقی تمام‌صفحه: بزرگ، گوشه گرد، فاصله از بالا/پایین ──
            if (fsChatOpen) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .fillMaxWidth(0.42f)
                        .widthIn(max = 360.dp)
                        .padding(vertical = 40.dp)
                        .padding(end = 10.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(BrandCard.copy(alpha = 0.97f))
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "💬 چت زنده",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = BrandText,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .clickable { fsChatOpen = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painterResource(com.hamfilm.app.R.drawable.ic_hf_close),
                                    "بستن چت",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = BrandCardLight)
                        ChatPanel(
                            messages = messages, typing = typing, listState = listState,
                            chatText = chatText, onChatText = { chatText = it },
                            onSend = { vm.sendChat(chatText); chatText = ""; vm.notifyTyping(false) },
                            onTyping = { on -> vm.notifyTyping(on) },
                            onReaction = { vm.sendReaction(it) },
                            myId = vm.myId,
                            seenById = { vm.isSeenByOthers(it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── دکمه چت شناور کنار صفحه — همیشه visible (هرگز ناپدید نمی‌شود) ──
            if (!fsChatOpen) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 14.dp)
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(BrandPurple, BrandCyan)))
                        .clickable { fsChatOpen = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painterResource(com.hamfilm.app.R.drawable.ic_hf_chat), "چت زنده", tint = Color.Unspecified, modifier = Modifier.size(25.dp))
                    if (unread > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFFF43F5E))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(unread.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // نوتیف پیام‌ها — راست بالا (RTL: TopStart = راست فیزیکی)، همیشه visible
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 10.dp, start = 14.dp)
            ) {
                ChatNotifyStack(messages = messages, myId = vm.myId, onOpenChat = { fsChatOpen = true })
            }
        }
    } else if (isLandscape) {
        // ═══ حالت افقی (غیر تمام‌صفحه): ویدیو سمت راست + چت کنارش ═══
        Row(Modifier.fillMaxSize().background(RoomBlueBg)) {
            Column(Modifier.weight(1.5f).fillMaxHeight().statusBarsPadding()) {
                RoomTopBar(
                    vm = vm, roomCode = roomCode, socketState = socketState,
                                         onCopy = { copyRoomCode(context, roomCode) },
                                         onMenu = { optionsOpen = true }
                )
                ConnectionBar(socketState)
                VideoSection(
                    player = player, vm = vm, fileInfo = fileInfo,
                    isPlaying = playerPlaying,
                    roomName = vm.roomName,
                    roomCode = roomCode,
                    onPlayPause = ::onPlayPause,
                    onSeekBy = ::onSeekBy,
                    onSeekTo = ::onSeekTo,
                    onFullscreen = { fullscreen = !fullscreen },
                    onPlayerViewReady = { playerViewRef = it },
                    playerPosMs = playerPosMs,
                    playerDurMs = playerDurMs,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )
            }
            // چت کنار صفحه
            ChatPanel(
                messages = messages, typing = typing, listState = listState,
                chatText = chatText, onChatText = { chatText = it },
                onSend = {
                    vm.sendChat(chatText); chatText = ""
                    vm.notifyTyping(false)
                },
                onTyping = { on -> vm.notifyTyping(on) },
                onReaction = { vm.sendReaction(it) },
                myId = vm.myId,
                seenById = { vm.isSeenByOthers(it) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(RoomBlueBg.copy(alpha = 0.9f))
                    .animateContentSize()
            )
        }
    } else {
        // ═══ حالت عمودی — طراحی Premium ═══
        Box(Modifier.fillMaxSize().background(RoomBlueBg)) {
            // ── محتوای اسکرول‌شونده ──
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 116.dp)    // فضای هدر پین‌شده
                    .padding(bottom = 132.dp) // فضای ناوبری شناور
            ) {
                // ── ۲) Video Player ──
                VideoSection(
                    player = player, vm = vm, fileInfo = fileInfo,
                    isPlaying = playerPlaying,
                    roomName = vm.roomName,
                    roomCode = roomCode,
                    onPlayPause = ::onPlayPause,
                    onSeekBy = ::onSeekBy,
                    onSeekTo = ::onSeekTo,
                    onFullscreen = { fullscreen = !fullscreen },
                    onPlayerViewReady = { playerViewRef = it },
                    playerPosMs = playerPosMs,
                    playerDurMs = playerDurMs,
                    showRoomName = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .aspectRatio(16f / 9f)
                )

                // ── ۳) Quick Actions ──
                QuickActionsRow(
                    unread = unread,
                    peerCount = peers.size,
                    onChat = { chatOpen = true },
                    onMembers = { membersOpen = true },
                    onInvite = { shareRoomCode(context, roomCode) },
                    onGuest = { guestDialogOpen = true }
                )

                // ── ۴) ابزارهای اتاق ──
                SectionTitle("ابزارهای اتاق")
                ToolsGrid(
                    onPickFile = ::pickLocalFile,
                    onUrlDialog = { urlDialogOpen = true }
                )

                // ── ۹) اکشن‌های پایین ──
                Spacer(Modifier.height(10.dp))
                BottomActionsRow(
                    onHelp = { helpDialogOpen = true },
                    onExit = { leaveConfirmOpen = true }
                )
                Spacer(Modifier.height(6.dp))
            }

            // ── ۱) هدر پین‌شده (همیشه بالاست) ──
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(RoomBlueBg.copy(alpha = 0.97f))
            ) {
                RoomTopBar(
                    vm = vm, roomCode = roomCode, socketState = socketState,
                                         onCopy = { copyRoomCode(context, roomCode) },
                                         onMenu = { optionsOpen = true }
                )
                ConnectionBar(socketState)
            }

            // ── نوتیف پیام‌ها (بالا-راست) ──
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 64.dp, end = 10.dp)
            ) {
                ChatNotifyStack(messages = messages, myId = vm.myId, onOpenChat = { chatOpen = true })
            }

            // ── چت: اورلی شیشه‌ای از پایین ──
            AnimatedVisibility(
                visible = chatOpen,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                enter = slideInVertically(tween(320)) { it / 4 } + fadeIn(tween(320)),
                exit = slideOutVertically(tween(240)) { it / 4 } + fadeOut(tween(240))
            ) {
                ChatOverlay(
                    messages = messages, typing = typing, listState = listState,
                    chatText = chatText, onChatText = { chatText = it },
                    onSend = { vm.sendChat(chatText); chatText = ""; vm.notifyTyping(false) },
                    onTyping = { on -> vm.notifyTyping(on) },
                    onReaction = { vm.sendReaction(it) },
                    myId = vm.myId,
                    seenById = { vm.isSeenByOthers(it) },
                    unread = unread,
                    onClose = { chatOpen = false }
                )
            }

        }
    }

    // ---------- دیالوگ اعضا ----------
    if (membersOpen) {
        MembersDialog(
            peers = peers,
            myId = vm.myId,
            isHost = vm.isHost,
            onClose = { membersOpen = false },
            onKick = { vm.kick(it) }
        )
    }

    // ---------- شیت آپشن‌ها (منوی ۳ خط) ----------
    if (optionsOpen) {
        RoomOptionsSheet(
            onClose = { optionsOpen = false },
            onPickFile = { optionsOpen = false; pickLocalFile() },
            onUrlDialog = { optionsOpen = false; urlDialogOpen = true },
            onCopy = { optionsOpen = false; copyRoomCode(context, roomCode) },
            onShare = { optionsOpen = false; shareRoomCode(context, roomCode) },
            onMembers = { optionsOpen = false; membersOpen = true },
            onPlayerSettings = { optionsOpen = false; playerSettingsOpen = true },
            isHost = vm.isHost,
            locked = vm.roomLocked,
            speed = vm.playbackSpeed,
            onSpeed = { rate -> vm.setSpeed(rate) },
            onLock = { locked -> vm.lock(locked); optionsOpen = false },
            onLeave = { optionsOpen = false; leaveConfirmOpen = true }
        )
    }

    // ---------- دیالوگ راهنما ----------
    if (helpDialogOpen) {
        AlertDialog(
            onDismissRequest = { helpDialogOpen = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = BrandCard,
            title = { Text("📖 راهنمای سریع اتاق", color = BrandText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelpRow("🎬", "پخش/توقف با دکمه وسط پلیر — فقط میزبان کنترل می‌کند")
                    HelpRow("↺↻", "عقب و جلو ۱۰ ثانیه — برای جستجوی دقیق")
                    HelpRow("💬", "چت با دوستان — پیام‌های جدید با صدا اعلام می‌شوند")
                    HelpRow("🍔", "منوی ☰ بالای صفحه: همه ابزارهای اتاق")
                    HelpRow("👥", "اعضا را از Quick Actions یا منو مدیریت کن")
                }
            },
            confirmButton = {
                GradientButton("باشه", onClick = { helpDialogOpen = false }, modifier = Modifier.fillMaxWidth())
            }
        )
    }

    // ---------- دیالوگ دسترسی مهمان ----------
    if (guestDialogOpen) {
        AlertDialog(
            onDismissRequest = { guestDialogOpen = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = BrandCard,
            title = { Text("🎫 دسترسی مهمان", color = BrandText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "هر کسی با کد اتاق می‌تواند به‌عنوان مهمان وارد شود. میزبان اعضا را از «کاربران» مدیریت می‌کند.",
                        color = BrandTextMuted,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        roomCode,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp,
                        color = BrandCyan
                    )
                }
            },
            confirmButton = {
                GradientButton(
                    "کپی کد",
                    onClick = { guestDialogOpen = false; copyRoomCode(context, roomCode) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                TextButton(onClick = { guestDialogOpen = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("بستن", color = BrandTextMuted)
                }
            }
        )
    }

    // ---------- دیالوگ تأیید خروج از اتاق ----------
    if (leaveConfirmOpen) {
        AlertDialog(
            onDismissRequest = { leaveConfirmOpen = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = BrandCard,
            title = {
                Text(
                    "خروج از اتاق؟",
                    color = BrandText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    "از اتاق خارج می‌شوی؟ دوستانت بدون تو به تماشا ادامه می‌دهند.",
                    color = BrandTextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                GradientButton(
                    "خروج از اتاق",
                    onClick = {
                        leaveConfirmOpen = false
                        vm.disconnect()
                        nav.popBackStack()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { leaveConfirmOpen = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("می‌مانم", color = BrandCyan, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // ---------- دیالوگ لینک ویدیو ----------
    if (urlDialogOpen) {
        ChangeVideoDialog(
            onClose = { urlDialogOpen = false },
            onConfirm = { url ->
                urlDialogOpen = false
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.playWhenReady = true
                vm.changeVideo(url)
            }
        )
    }

    // ---------- شیت تنظیمات پلیر (ترک صوتی/زیرنویس/رنگ) ----------
    if (playerSettingsOpen) {
        com.hamfilm.app.ui.components.PlayerSettingsSheet(
            player = player,
            onClose = { playerSettingsOpen = false },
            onSubtitleFile = { pickSubtitle() },
            subtitleColorIndex = subtitleColorIndex,
            onSubtitleColor = { i ->
                applySubtitleColor(i)
            },
            onSubtitleEnabled = { en ->
                subtitleEnabled = en
                if (!en) {
                    // حذف زیرنویس
                    val cur = player.currentMediaItem ?: return@PlayerSettingsSheet
                    player.setMediaItem(cur.buildUpon().setSubtitleConfigurations(emptyList()).build())
                    player.prepare()
                }
            },
            subtitleEnabled = subtitleEnabled
        )
    }

    // ---------- اخراج ----------
    if (vm.kickedOut) {
        AlertDialog(
            onDismissRequest = {},
            shape = RoundedCornerShape(24.dp),
            containerColor = BrandCard,
            title = { Text("از اتاق اخراج شدی", color = BrandText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = { Text("مدیر اتاق تو را اخراج کرد.", color = BrandTextMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                GradientButton("باشه", onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth())
            }
        )
    }
}

// ============================================================
//  نوار وضعیت اتصال — بسیار ظریف، زیر هدر
// ============================================================
@Composable
private fun ConnectionBar(socketState: SocketState) {
    val (text, color) = when (socketState) {
        is SocketState.Connected -> "متصل" to BrandGreen
        is SocketState.Connecting -> "در حال اتصال…" to BrandAmber
        is SocketState.Error -> "قطع اتصال — تلاش مجدد…" to BrandDanger
        else -> "قطع اتصال" to BrandDanger
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        // نقطه وضعیت در انتها (سمت چپ در RTL)
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.5f))
        )
    }
}

// ============================================================
//  هدر اتاق — Compact و Premium: دکمه تنظیمات | کارت اتاق | Share/Lock/Menu
// ============================================================
@Composable
private fun RoomTopBar(
    vm: RoomViewModel,
    roomCode: String,
    socketState: SocketState,
    onCopy: () -> Unit,
    onMenu: () -> Unit
) {
    val peers by vm.peers.collectAsState()
    val locked = vm.roomLocked
    val connected = socketState is SocketState.Connected

    Box(
        Modifier
            .fillMaxWidth()
            .background(RoomBlueBg)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── مرکز: کارت وضعیت اتاق ──
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onCopy)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // نقطه زنده
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (connected) BrandGreen else BrandDanger)
                )
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "اتاق " + vm.roomName.ifBlank { roomCode },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFEAF6FF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        roomCode,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = BrandCyan.copy(alpha = 0.9f)
                    )
                }
                if (locked) {
                    Text("🔒", fontSize = 11.sp)
                    Spacer(Modifier.width(4.dp))
                }
                if (connected) {
                    Text("آنلاین", fontSize = 9.sp, color = BrandGreen, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.width(8.dp))

            // ── سمت چپ (پایان RTL): Lock (میزبان) + Menu — کوچک و شیشه‌ای ──
            if (vm.isHost) {
                GlassIconButton(
                    iconRes = if (locked) com.hamfilm.app.R.drawable.ic_hf_lock else com.hamfilm.app.R.drawable.ic_hf_lock_open,
                    contentDescription = "قفل اتاق",
                    tint = if (locked) Color(0xFFFCA5A5) else Color(0xFFFCD34D),
                    onClick = { vm.lock(!locked) }
                )
                Spacer(Modifier.width(6.dp))
            }
            GlassIconButton(
                iconRes = com.hamfilm.app.R.drawable.ic_hf_menu,
                contentDescription = "منوی اتاق",
                tint = Color(0xFFC7D2FE),
                onClick = onMenu
            )
        }
    }
}

/** دکمه شیشه‌ای کوچک هدر — ۳۶dp */
@Composable
private fun GlassIconButton(
    iconRes: Int,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    ScaleTap(onClick = onClick) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(iconRes),
                contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ============================================================
//  Video Player — کارت Premium: کیفیت، تمام‌صفحه، ±۱۰ ثانیه، تایم‌لاین
// ============================================================
@Composable
private fun VideoSection(
    player: Player,
    vm: RoomViewModel,
    fileInfo: WsFileInfo?,
    isPlaying: Boolean,
    roomName: String,
    roomCode: String,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onFullscreen: () -> Unit,
    onPlayerViewReady: (PlayerView) -> Unit = {},
    playerPosMs: Long,
    playerDurMs: Long,
    showControls: Boolean = true,
    showRoomName: Boolean = true,
    isFullscreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape = if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(20.dp)
    Box(
        modifier
            .clip(shape)
            .background(Color.Black)
            .then(
                if (isFullscreen) Modifier
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            )
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    controllerAutoShow = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }.also { onPlayerViewReady(it) }
            },
            modifier = Modifier.fillMaxSize()
        )

        // گرادیان ظریف برای خوانایی کنترل‌ها
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.55f)
                        )
                    )
                )
        )

        // ── بالا: کیفیت (چپ فیزیکی) + نشانگر فایل ──
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            fileInfo?.let { fi ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "🎬 " + fi.name,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 150.dp)
                    )
                }
            }
        }

        // ── بالا: دکمه تمام‌صفحه (راست فیزیکی) — همیشه visible ──
        ScaleTap(onClick = onFullscreen) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(10.dp)
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isFullscreen) painterResource(com.hamfilm.app.R.drawable.ic_hf_fullscreen_exit)
                    else painterResource(com.hamfilm.app.R.drawable.ic_hf_fullscreen),
                    if (isFullscreen) "خروج از تمام‌صفحه" else "تمام‌صفحه",
                    tint = if (isFullscreen) Color(0xFF7DD3FC) else Color(0xFFE879F9),
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        // ── مرکز: عقب ۱۰ / پخش / جلو ۱۰ ──
        if (showControls) {
            Row(
                Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                ScaleTap(onClick = { onSeekBy(-10_000) }) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("↺", fontSize = 22.sp, color = Color.White)
                    }
                }
                ScaleTap(onClick = onPlayPause) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(BrandPurple.copy(alpha = 0.9f), BrandCyan.copy(alpha = 0.9f)))
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isPlaying) painterResource(com.hamfilm.app.R.drawable.ic_hf_pause)
                            else painterResource(com.hamfilm.app.R.drawable.ic_hf_play),
                            "پخش/توقف",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                ScaleTap(onClick = { onSeekBy(10_000) }) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("↻", fontSize = 22.sp, color = Color.White)
                    }
                }
            }

            // ── پایین: تایم‌لاین + زمان + تنظیمات ──
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatTime(playerPosMs),
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                val dragState = remember { mutableStateOf<Long?>(null) }
                Slider(
                    value = if (playerDurMs > 0) (dragState.value ?: playerPosMs).toFloat().coerceIn(0f, playerDurMs.toFloat()) else 0f,
                    onValueChange = { dragState.value = it.toLong() },
                    onValueChangeFinished = {
                        onSeekTo(dragState.value ?: playerPosMs)
                        dragState.value = null
                    },
                    valueRange = 0f..if (playerDurMs > 0) playerDurMs.toFloat() else 1f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = BrandCyan,
                        activeTrackColor = BrandCyan,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    )
                )
                Text(
                    formatTime(playerDurMs),
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }

        // ── پیام «هنوز فیلمی انتخاب نشده» ──
        if (vm.videoUrl.isBlank() && fileInfo == null) {
            Text(
                "هنوز فیلمی انتخاب نشده — از ابزارهای اتاق انتخاب کن",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // واکنش‌های لحظه‌ای (فقط از طرف دیگران)
        ReactionBurst(vm)
    }
}
}

/** زمان 00:00 */
private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(java.util.Locale.US, "%02d:%02d", m, s)
}

// ============================================================
//  Quick Actions — حداکثر ۵ کارت کوچک
// ============================================================
@Composable
private fun QuickActionsRow(
    unread: Int,
    peerCount: Int,
    onChat: () -> Unit,
    onMembers: () -> Unit,
    onInvite: () -> Unit,
    onGuest: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickActionCard(
            iconRes = com.hamfilm.app.R.drawable.ic_hf_chat,
            label = "چت متنی",
            sub = "پیام و پاسخ",
            tint = BrandCyan,
            badge = unread,
            onClick = onChat,
            modifier = Modifier.weight(1f)
        )
        QuickActionCard(
            iconRes = com.hamfilm.app.R.drawable.ic_hf_users,
            label = "کاربران",
            sub = "مدیریت کاربران",
            tint = Color(0xFF6EE7B7),
            badge = peerCount,
            onClick = onMembers,
            modifier = Modifier.weight(1f)
        )
        QuickActionCard(
            iconRes = com.hamfilm.app.R.drawable.ic_hf_share,
            label = "دعوت",
            sub = "دعوت از کاربر",
            tint = Color(0xFFFBBF24),
            onClick = onInvite,
            modifier = Modifier.weight(1f)
        )
        QuickActionCard(
            iconRes = com.hamfilm.app.R.drawable.ic_hf_guest,
            label = "مهمان",
            sub = "دسترسی مهمان",
            tint = Color(0xFFF472B6),
            onClick = onGuest,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickActionCard(
    iconRes: Int,
    label: String,
    sub: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Int = 0
) {
    ScaleTap(onClick = onClick, modifier = modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painterResource(iconRes), null, tint = tint, modifier = Modifier.size(17.dp))
                }
                if (badge > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFF43F5E))
                            .padding(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text(badge.toString(), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = BrandText)
            Text(sub, fontSize = 8.sp, color = BrandTextMuted, maxLines = 1)
        }
    }
}

// ============================================================
//  عنوان بخش
// ============================================================
@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        color = BrandText,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
    )
}

// ============================================================
//  ابزارهای اتاق — گرید دو ستونه
// ============================================================
@Composable
private fun ToolsGrid(
    onPickFile: () -> Unit,
    onUrlDialog: () -> Unit
) {
    val tools = mutableListOf<Pair<Pair<Int, String>, () -> Unit>>()
    tools.add((com.hamfilm.app.R.drawable.ic_hf_folder to "انتخاب فیلم از گوشی") to onPickFile)
    tools.add((com.hamfilm.app.R.drawable.ic_hf_link to "تغییر ویدیو با لینک") to onUrlDialog)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tools.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { (item, action) ->
                    val (icon, title) = item
                    ToolCell(iconRes = icon, title = title, onClick = action, modifier = Modifier.weight(1f))
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ToolCell(
    iconRes: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ScaleTap(onClick = onClick, modifier = modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Color.White.copy(alpha = 0.045f))
                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(15.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(BrandCyan.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(iconRes), null, tint = BrandCyan, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(9.dp))
            Text(
                title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = BrandText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

//  اکشن‌های پایین: راهنما | خروج از حساب | خروج از اتاق
// ============================================================
@Composable
private fun BottomActionsRow(
    onHelp: () -> Unit,
    onExit: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // راهنما — آبی/خنثی
        ScaleTap(onClick = onHelp, modifier = Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF3B82F6).copy(alpha = 0.12f))
                    .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("راهنما", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF93C5FD))
            }
        }
        // خروج از اتاق — قرمز (خطرناک)
        ScaleTap(onClick = onExit, modifier = Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("خروج از اتاق", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFCA5A5))
            }
        }
    }
}

//  چت — اورلی شیشه‌ای از پایین (حالت عمودی)
// ============================================================
@Composable
private fun ChatOverlay(
    messages: List<WsMessage>,
    typing: Set<String>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    chatText: String,
    onChatText: (String) -> Unit,
    onSend: () -> Unit,
    onTyping: (Boolean) -> Unit,
    onReaction: (String) -> Unit,
    myId: String,
    seenById: (Long) -> Boolean,
    unread: Int,
    onClose: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.74f)
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(BrandCard.copy(alpha = 0.97f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
    ) {
        Column(Modifier.fillMaxSize()) {
            // دستگیره + هدر
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.2f))
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "💬 چت زنده",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = BrandText,
                    modifier = Modifier.weight(1f)
                )
                if (unread > 0) {
                    Text("$unread جدید", fontSize = 10.sp, color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                }
                ScaleTap(onClick = onClose) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painterResource(com.hamfilm.app.R.drawable.ic_hf_close), "بستن چت", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
            HorizontalDivider(color = BrandCardLight)
            ChatPanel(
                messages = messages, typing = typing, listState = listState,
                chatText = chatText, onChatText = onChatText,
                onSend = onSend,
                onTyping = onTyping,
                onReaction = onReaction,
                myId = myId,
                seenById = seenById,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ============================================================
//  نوتیف پیام‌های جدید چت (بالا سمت راست — مثل سایت)
// ============================================================
@Composable
private fun ChatNotifyStack(
    messages: List<WsMessage>,
    myId: String,
    onOpenChat: () -> Unit
) {
    var toasts by remember { mutableStateOf<List<WsMessage>>(emptyList()) }
    var prevCount by remember { mutableStateOf(0) }
    val context = LocalContext.current

    LaunchedEffect(messages.size) {
        val prev = prevCount
        prevCount = messages.size
        if (prev == 0 || messages.size <= prev) return@LaunchedEffect
        val last = messages.lastOrNull() ?: return@LaunchedEffect
        if (last.system || (last.senderId.isNotBlank() && last.senderId == myId)) return@LaunchedEffect
        toasts = (toasts + last).takeLast(3)
        // صدای بلند پیام جدید (مثل نوتیف گوشی)
        playMessageSound(context)
        kotlinx.coroutines.delay(4000)
        toasts = toasts.filterNot { it.id == last.id }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.Start
    ) {
        toasts.forEach { m ->
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BrandCard.copy(alpha = 0.97f))
                    .clickable(onClick = onOpenChat)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarImage(avatarId = m.avatar, size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.widthIn(max = 230.dp)) {
                    Text(m.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BrandCyan)
                    Text(m.text, fontSize = 14.sp, color = BrandText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** صدای بلند پیام جدید — از صدای نوتیف گوشی استفاده می‌شود */
private fun playMessageSound(context: Context) {
    try {
        val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        android.media.RingtoneManager.getRingtone(context, uri)?.play()
    } catch (_: Exception) { }
}

// ============================================================
//  پنل چت (هم در حالت عمودی هم کنار صفحه در افقی)
// ============================================================
@Composable
private fun ChatPanel(
    messages: List<WsMessage>,
    typing: Set<String>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    chatText: String,
    onChatText: (String) -> Unit,
    onSend: () -> Unit,
    onTyping: (Boolean) -> Unit,
    onReaction: (String) -> Unit,
    myId: String,
    seenById: (Long) -> Boolean = { false },
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var typingJob by remember { mutableStateOf<Job?>(null) }

    Column(modifier.fillMaxWidth()) {
        // لیست پیام‌ها
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages, key = { it.id }) { m ->
                val isMine = m.senderId.isNotBlank() && m.senderId == myId
                MessageRow(m, isMe = isMine, seen = if (isMine) seenById(m.ts) else false)
            }
            if (typing.isNotEmpty()) {
                item { Text("✍️ ${typing.size} نفر در حال تایپ…", fontSize = 12.sp, color = BrandTextMuted) }
            }
        }

        // واکنش‌ها
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(Emojis) { e ->
                Text(
                    e,
                    fontSize = 19.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onReaction(e) }
                        .padding(5.dp)
                )
            }
        }

        // ورودی
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = chatText,
                onValueChange = {
                    onChatText(it)
                    onTyping(it.isNotBlank())
                    typingJob?.cancel()
                    if (it.isNotBlank()) {
                        typingJob = scope.launch {
                            delay(2000)
                            onTyping(false)
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("پیام بنویسید…", color = BrandTextMuted) },
                shape = RoundedCornerShape(24.dp),
                maxLines = 2,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandCyan,
                    unfocusedBorderColor = BrandPurple.copy(alpha = 0.3f),
                    focusedContainerColor = BrandCardLight.copy(alpha = 0.4f),
                    unfocusedContainerColor = BrandCardLight.copy(alpha = 0.25f),
                    cursorColor = BrandCyan
                )
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(BrandPurple, BrandCyan)))
                    .clickable { onSend() },
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(com.hamfilm.app.R.drawable.ic_hf_send), "ارسال", tint = Color.Unspecified, modifier = Modifier.size(21.dp))
            }
        }
    }
}

// ============================================================
//  شیت آپشن‌های اتاق
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomOptionsSheet(
    onClose: () -> Unit,
    onPickFile: () -> Unit,
    onUrlDialog: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onMembers: () -> Unit,
    onPlayerSettings: () -> Unit,
    isHost: Boolean,
    locked: Boolean,
    speed: Double,
    onSpeed: (Double) -> Unit,
    onLock: (Boolean) -> Unit,
    onLeave: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = BrandCard,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            Modifier.padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                "منوی اتاق",
                style = MaterialTheme.typography.titleLarge,
                color = BrandText,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            // ── ویدیو ──
            Text("🎬 ویدیو", fontSize = 11.sp, color = BrandCyan, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
            SheetRow(com.hamfilm.app.R.drawable.ic_hf_folder, "انتخاب فیلم از گوشی", BrandCyan, onPickFile)
            SheetRow(com.hamfilm.app.R.drawable.ic_hf_link, "تغییر ویدیو با لینک", BrandPurple, onUrlDialog)
            SheetRow(com.hamfilm.app.R.drawable.ic_hf_settings, "تنظیمات پلیر (صدا/زیرنویس/کیفیت/سرعت)", Color(0xFFF472B6), onPlayerSettings)
            HorizontalDivider(color = BrandCardLight, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

            // ── اتاق ──
            Text("🚪 اتاق", fontSize = 11.sp, color = BrandCyan, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
            SheetRow(com.hamfilm.app.R.drawable.ic_hf_users, "اعضای اتاق", Color(0xFF6EE7B7), onMembers)
            SheetRow(com.hamfilm.app.R.drawable.ic_hf_copy, "کپی کد اتاق", BrandText, onCopy)
            SheetRow(com.hamfilm.app.R.drawable.ic_hf_share, "دعوت دوستان", BrandGreen, onShare)
            if (isHost) {
                SheetRow(
                    if (locked) com.hamfilm.app.R.drawable.ic_hf_lock_open else com.hamfilm.app.R.drawable.ic_hf_lock,
                    if (locked) "باز کردن قفل اتاق" else "قفل کردن اتاق",
                    BrandAmber
                ) { onLock(!locked) }
            }
            HorizontalDivider(color = BrandCardLight, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

            // ── سرعت پخش همگام ──
            Text("سرعت پخش", fontSize = 11.sp, color = BrandTextMuted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0.5, 0.75, 1.0, 1.25, 1.5, 2.0).forEach { r ->
                    val active = kotlin.math.abs(speed - r) < 0.01
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (active) Brush.linearGradient(listOf(BrandPurple.copy(alpha = 0.35f), BrandCyan.copy(alpha = 0.25f)))
                                else androidx.compose.ui.graphics.SolidColor(BrandCardLight)
                            )
                            .clickable { onSpeed(r) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(if (r == 1.0) "۱x" else "${r}x", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (active) BrandCyan else BrandTextMuted)
                    }
                }
            }
            HorizontalDivider(color = BrandCardLight, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            SheetRow(com.hamfilm.app.R.drawable.ic_hf_exit, "خروج از اتاق", BrandDanger, onLeave)
        }
    }
}

@Composable
private fun SheetRow(iconRes: Int, title: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(iconRes), null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, fontSize = 14.5.sp, color = BrandText)
    }
}

// ============================================================
//  دیالوگ تغییر ویدیو با لینک
// ============================================================
@Composable
private fun ChangeVideoDialog(onClose: () -> Unit, onConfirm: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(24.dp),
        containerColor = BrandCard,
        title = { Text("تغییر ویدیو", color = BrandText) },
        text = {
            Column {
                HamTextField(
                    url, { url = it; error = null },
                    "لینک ویدیو (MP4 / HLS / یوتیوب)",
                    placeholder = "https://example.com/movie.mp4"
                )
                error?.let { Spacer(Modifier.height(6.dp)); Text(it, color = BrandDanger, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            GradientButton(
                text = "پخش",
                onClick = {
                    val v = url.trim()
                    if (v.isBlank()) { error = "لینک را وارد کن"; return@GradientButton }
                    if (!v.startsWith("http")) { error = "فقط لینک‌های http/https"; return@GradientButton }
                    onConfirm(v)
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("انصراف", color = BrandTextMuted) }
        }
    )
}

// ============================================================
//  بقیه کامپوننت‌ها
// ============================================================

@Composable
private fun ReactionBurst(vm: RoomViewModel) {
    val scope = rememberCoroutineScope()
    var reactions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(Unit) {
        vm.reactions.collect { (name, emoji) ->
            val id = System.nanoTime()
            reactions = reactions + (id.toString() to emoji)
            scope.launch {
                delay(2200)
                reactions = reactions.filterNot { it.first == id.toString() }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        reactions.forEach { (id, emoji) ->
            var progress by remember { mutableStateOf(0f) }
            LaunchedEffect(id) {
                while (progress < 1f) {
                    progress += 0.03f
                    delay(30)
                }
            }
            val alpha by animateFloatAsState(1f - progress, tween(30))
            Text(
                emoji,
                fontSize = (36 + progress * 40).sp,
                color = Color.White.copy(alpha = alpha),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-progress * 260).dp)
            )
        }
    }
}

@Composable
private fun PeerChip(p: WsPeer, isMe: Boolean) {
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isMe) Brush.linearGradient(listOf(BrandPurple.copy(alpha = 0.35f), BrandCyan.copy(alpha = 0.15f)))
                else androidx.compose.ui.graphics.SolidColor(BrandCard)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            AvatarImage(avatarId = p.avatar, size = 28.dp)
            if (p.isLeader) {
                Text(
                    "👑",
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (isMe) "${p.name} (من)" else p.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 90.dp)
        )
    }
}

@Composable
private fun MessageRow(m: WsMessage, isMe: Boolean, seen: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        if (m.system) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrandCardLight)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(m.text, fontSize = 11.sp, color = BrandTextMuted)
            }
        } else {
            Row(
                Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 14.dp, topEnd = 14.dp,
                            bottomStart = if (isMe) 14.dp else 4.dp,
                            bottomEnd = if (isMe) 4.dp else 14.dp
                        )
                    )
                    .background(if (isMe) BrandPurple.copy(alpha = 0.85f) else BrandCard)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(Modifier.widthIn(max = 240.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (m.avatar.isNotBlank() && !isMe) {
                            AvatarImage(avatarId = m.avatar, size = 14.dp)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(m.name, fontSize = 11.sp, color = if (isMe) Color.White.copy(0.9f) else BrandCyan, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(m.text, fontSize = 14.sp, color = if (isMe) Color.White else BrandText)
                }
                Spacer(Modifier.width(8.dp))
                // زمان + تیک رسید مطالعه (فقط پیام خودم)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(m.ts)),
                        fontSize = 9.sp,
                        color = if (isMe) Color.White.copy(0.6f) else BrandTextMuted
                    )
                    if (isMe) {
                        Spacer(Modifier.height(2.dp))
                        Icon(
                            if (seen) painterResource(com.hamfilm.app.R.drawable.ic_hf_done_all) else painterResource(com.hamfilm.app.R.drawable.ic_hf_done),
                            contentDescription = if (seen) "دیده شد" else "ارسال شد",
                            tint = if (seen) Color(0xFF81C784) else Color.White.copy(0.55f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MembersDialog(
    peers: List<WsPeer>,
    myId: String,
    isHost: Boolean,
    onClose: () -> Unit,
    onKick: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(24.dp),
        containerColor = BrandCard,
        title = { Text("اعضای اتاق (${peers.size})", color = BrandText) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(peers, key = { it.id }) { p ->
                    val isMe = p.id == myId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isMe) Brush.linearGradient(listOf(Color(0xFF2563EB).copy(alpha = 0.25f), BrandPurple.copy(alpha = 0.12f)))
                                else androidx.compose.ui.graphics.SolidColor(BrandCardLight)
                            )
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarImage(avatarId = p.avatar, size = 42.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isMe) "${p.name} (من)" else p.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = BrandText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                when {
                                    p.isLeader -> "👑 مدیر اتاق"
                                    isMe -> "شما"
                                    else -> "عضو"
                                },
                                fontSize = 11.sp,
                                color = if (p.isLeader) BrandAmber else if (isMe) BrandCyan else BrandTextMuted
                            )
                        }
                        if (isHost && !isMe) {
                            IconButton(onClick = { onKick(p.id); onClose() }) {
                                Icon(painterResource(com.hamfilm.app.R.drawable.ic_hf_delete), "اخراج", tint = BrandDanger, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("بستن", color = BrandCyan) }
        }
    )
}

@Composable
private fun HelpRow(icon: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = BrandTextMuted, modifier = Modifier.weight(1f))
    }
}

// ============================================================
//  ابزارهای اشتراک و کپی
// ============================================================
private fun copyRoomCode(context: Context, code: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("room-code", code))
    android.widget.Toast.makeText(context, "کد اتاق کپی شد: $code", android.widget.Toast.LENGTH_SHORT).show()
}

private fun shareRoomCode(context: Context, code: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "🎬 بیا با هم فیلم ببینیم!\nکد اتاق هم‌فیلم: $code")
    }
    context.startActivity(Intent.createChooser(send, "دعوت دوستان"))
}

private fun Context.queryDisplayName(uri: Uri): String = runCatching {
    val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
    name ?: "فایل محلی"
}.getOrDefault("فایل محلی")

private fun Context.querySize(uri: Uri): Long = runCatching {
    val size = contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
    }
    size ?: 0L
}.getOrDefault(0L)

/** SHA-256 فایل (برای همگام‌سازی فایل محلی با بقیه) */
private fun Context.queryFileHash(uri: Uri): String = runCatching {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    contentResolver.openInputStream(uri)?.use { input ->
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
        }
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}.getOrDefault("")

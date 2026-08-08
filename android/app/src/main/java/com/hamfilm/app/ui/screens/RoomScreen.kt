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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.hamfilm.app.data.ApiConfig
import com.hamfilm.app.data.TokenStore
import com.hamfilm.app.data.ws.*
import com.hamfilm.app.ui.components.*
import com.hamfilm.app.ui.theme.*
import com.hamfilm.app.viewmodel.RoomViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Locale

private val Emojis = listOf("❤️", "😂", "😮", "👍", "😢", "🔥", "🎬", "🍿")
private val Speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

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

    // کنترل راه دور → پلیر (وضعیت همگام از سرور)
    vm.onRemoteState = { playing, timeSec, speed ->
        player.playWhenReady = playing
        player.setPlaybackSpeed(speed.toFloat().coerceIn(0.25f, 4f))
        val target = (timeSec * 1000).toLong()
        // اگر اختلاف با موقعیت فعلی بیش از ۱.۵ ثانیه بود → سیک کن (بدون وقفه بی‌مورد)
        if (target > 0 && kotlin.math.abs(player.currentPosition - target) > 1500) {
            player.seekTo(target)
        }
    }
    vm.onRemoteCorrect = { timeSec, playing ->
        val target = (timeSec * 1000).toLong()
        if (target > 0 && kotlin.math.abs(player.currentPosition - target) > 2000) {
            player.seekTo(target)
        }
        player.playWhenReady = playing
    }
    vm.onRemoteVideo = { url ->
        if (url.isNotBlank()) {
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
        }
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
            player.setMediaItem(MediaItem.fromUri(it))
            player.prepare()
            player.playWhenReady = true
            // hash محتوای فایل (برای همگام‌سازی) — در پس‌زمینه تا UI لگ نزند
            val hash = context.queryFileHash(it)
            vm.shareLocalFile(name, size, hash)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun pickLocalFile() {
        filePicker.launch(arrayOf("video/*", "audio/*"))
    }

    // ---------- state ها ----------
    var chatText by remember { mutableStateOf("") }
    var chatOpen by remember { mutableStateOf(!isLandscape) }
    var membersOpen by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    var urlDialogOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var typingJob by remember { mutableStateOf<Job?>(null) }

    val peers by vm.peers.collectAsState()
    val messages by vm.messages.collectAsState()
    val typing by vm.typing.collectAsState()
    val socketState by vm.socketState.collectAsState()
    val fileInfo by vm.fileInfo.collectAsState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun onPlayPause() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        player.playWhenReady = !player.playWhenReady
        vm.togglePlay(player.currentPosition)
    }

    // ---------- چیدمان ----------
    if (isLandscape) {
        // حالت افقی: ویدیو سمت راست، چت کنارش (پنل چت همیشه باز)
        Row(Modifier.fillMaxSize().background(BrandBg)) {
            Column(Modifier.weight(1.5f).fillMaxHeight()) {
                RoomTopBar(
                    vm = vm, roomCode = roomCode, socketState = socketState,
                    fullscreen = fullscreen,
                    onBack = { vm.disconnect(); nav.popBackStack() },
                    onFullscreen = { fullscreen = !fullscreen },
                    onShare = { shareRoomCode(context, roomCode) },
                    onCopy = { copyRoomCode(context, roomCode) },
                    onOptions = { optionsOpen = true },
                    onMembers = { membersOpen = true }
                )
                VideoSection(
                    player = player, vm = vm, fileInfo = fileInfo,
                    isPlaying = player.isPlaying,
                    onPlayPause = ::onPlayPause,
                    onPickFile = ::pickLocalFile,
                    onUrlDialog = { urlDialogOpen = true },
                    modifier = Modifier.weight(1f)
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
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(BrandCard.copy(alpha = 0.6f))
                    .animateContentSize()
            )
        }
    } else {
        // حالت عمودی
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            RoomTopBar(
                vm = vm, roomCode = roomCode, socketState = socketState,
                fullscreen = fullscreen,
                onBack = { vm.disconnect(); nav.popBackStack() },
                onFullscreen = { fullscreen = !fullscreen },
                onShare = { shareRoomCode(context, roomCode) },
                onCopy = { copyRoomCode(context, roomCode) },
                onOptions = { optionsOpen = true },
                onMembers = { membersOpen = true }
            )
            VideoSection(
                player = player, vm = vm, fileInfo = fileInfo,
                isPlaying = player.isPlaying,
                onPlayPause = ::onPlayPause,
                onPickFile = ::pickLocalFile,
                onUrlDialog = { urlDialogOpen = true },
                modifier = Modifier.aspectRatio(16f / 9f)
            )
            // نوار اعضا
            if (peers.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    items(peers, key = { it.id }) { p -> PeerChip(p, isMe = p.id == vm.myId) }
                }
            }
            // چت
            AnimatedVisibility(
                visible = chatOpen,
                enter = slideInVertically(tween(350)) { it / 3 } + fadeIn(tween(350)),
                exit = slideOutVertically(tween(250)) { it / 3 } + fadeOut(tween(250))
            ) {
                ChatPanel(
                    messages = messages, typing = typing, listState = listState,
                    chatText = chatText, onChatText = { chatText = it },
                    onSend = { vm.sendChat(chatText); chatText = ""; vm.notifyTyping(false) },
                    onTyping = { on -> vm.notifyTyping(on) },
                    onReaction = { vm.sendReaction(it) },
                    myId = vm.myId,
                    modifier = Modifier.fillMaxSize()
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
            onKick = { vm.kick(it) },
            onMute = { id, muted -> vm.mute(id, muted) }
        )
    }

    // ---------- شیت آپشن‌ها ----------
    if (optionsOpen) {
        RoomOptionsSheet(
            onClose = { optionsOpen = false },
            onPickFile = { optionsOpen = false; pickLocalFile() },
            onUrlDialog = { optionsOpen = false; urlDialogOpen = true },
            onCopy = { optionsOpen = false; copyRoomCode(context, roomCode) },
            onShare = { optionsOpen = false; shareRoomCode(context, roomCode) },
            isHost = vm.isHost,
            locked = vm.roomLocked,
            speed = vm.playbackSpeed,
            onSpeed = { rate -> vm.setSpeed(rate) },
            onLock = { locked -> vm.lock(locked); optionsOpen = false },
            onLeave = { optionsOpen = false; vm.disconnect(); nav.popBackStack() }
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

    // ---------- اخراج ----------
    if (vm.kickedOut) {
        AlertDialog(
            onDismissRequest = {},
            shape = RoundedCornerShape(24.dp),
            containerColor = BrandCard,
            title = { Text("از اتاق اخراج شدی", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = { Text("مدیر اتاق تو را اخراج کرد.", color = BrandTextMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                GradientButton("باشه", onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth())
            }
        )
    }
}

// ============================================================
//  نوار بالای اتاق
// ============================================================
@Composable
private fun RoomTopBar(
    vm: RoomViewModel,
    roomCode: String,
    socketState: SocketState,
    fullscreen: Boolean,
    onBack: () -> Unit,
    onFullscreen: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onOptions: () -> Unit,
    onMembers: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, "خروج", tint = BrandTextMuted) }
        Column(Modifier.weight(1f)) {
            Text(
                vm.roomName.ifBlank { "اتاق $roomCode" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("کد: $roomCode", fontSize = 11.sp, color = BrandCyan, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                when (socketState) {
                    is SocketState.Connected -> StatusBar("متصل", BrandGreen)
                    is SocketState.Connecting -> StatusBar("در حال اتصال…", BrandAmber)
                    else -> StatusBar("قطع", BrandDanger)
                }
            }
        }
        IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, "کپی کد", tint = BrandTextMuted, modifier = Modifier.size(19.dp)) }
        IconButton(onClick = onShare) { Icon(Icons.Default.Share, "دعوت", tint = BrandTextMuted, modifier = Modifier.size(19.dp)) }
        IconButton(onClick = onMembers) { Icon(Icons.Default.People, "اعضا", tint = BrandText, modifier = Modifier.size(20.dp)) }
        IconButton(onClick = onFullscreen) {
            Icon(
                if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                "تمام‌صفحه",
                tint = BrandText,
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(onClick = onOptions) { Icon(Icons.Default.MoreVert, "بیشتر", tint = BrandText, modifier = Modifier.size(20.dp)) }
    }
}

// ============================================================
//  بخش ویدیو
// ============================================================
@Composable
private fun VideoSection(
    player: Player,
    vm: RoomViewModel,
    fileInfo: WsFileInfo?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPickFile: () -> Unit,
    onUrlDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // نشانگر فایل محلی در حال پخش
        fileInfo?.let { fi ->
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    "🎬 میزبان: ${fi.name}",
                    fontSize = 11.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 220.dp)
                )
            }
        }

        // دکمه‌های شناور پایین ویدیو
        Row(
            Modifier.align(Alignment.BottomEnd).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // انتخاب فایل محلی
            FloatingActionButton(
                onClick = onPickFile,
                modifier = Modifier.size(42.dp),
                containerColor = BrandCyan.copy(alpha = 0.9f),
                shape = CircleShape
            ) {
                Icon(Icons.Default.FolderOpen, "فایل محلی", tint = Color(0xFF00333D), modifier = Modifier.size(20.dp))
            }
            // تغییر لینک
            FloatingActionButton(
                onClick = onUrlDialog,
                modifier = Modifier.size(42.dp),
                containerColor = BrandCard.copy(alpha = 0.9f),
                shape = CircleShape
            ) {
                Icon(Icons.Default.Link, "لینک ویدیو", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            // پخش/توقف
            FloatingActionButton(
                onClick = onPlayPause,
                modifier = Modifier.size(46.dp),
                containerColor = BrandPurple,
                shape = CircleShape
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // واکنش‌های لحظه‌ای
        ReactionBurst(vm)
    }
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
            items(messages, key = { it.id }) { m -> MessageRow(m, isMe = m.userId == myId) }
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
                    unfocusedBorderColor = BrandCardLight
                )
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = { onSend() },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = BrandPurple)
            ) {
                Icon(Icons.Default.Send, "ارسال", tint = Color.White)
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
        Column(Modifier.padding(bottom = 30.dp)) {
            Text(
                "گزینه‌های اتاق",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
            SheetRow(Icons.Default.FolderOpen, "انتخاب فیلم از گوشی", BrandCyan, onPickFile)
            SheetRow(Icons.Default.Link, "تغییر ویدیو با لینک", BrandPurple, onUrlDialog)
            SheetRow(Icons.Default.ContentCopy, "کپی کد اتاق", BrandText, onCopy)
            SheetRow(Icons.Default.Share, "دعوت دوستان", BrandGreen, onShare)
            if (isHost) {
                SheetRow(
                    if (locked) Icons.Default.LockOpen else Icons.Default.Lock,
                    if (locked) "باز کردن قفل اتاق" else "قفل کردن اتاق",
                    BrandAmber
                ) { onLock(!locked) }
            }
            // سرعت پخش همگام
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
                            .background(if (active) BrandGradientSoft else BrandCardLight)
                            .clickable { onSpeed(r) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(if (r == 1.0) "۱x" else "${r}x", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (active) BrandCyan else BrandTextMuted)
                    }
                }
            }
            HorizontalDivider(color = BrandCardLight, modifier = Modifier.padding(vertical = 6.dp))
            SheetRow(Icons.Default.ExitToApp, "خروج از اتاق", BrandDanger, onLeave)
        }
    }
}

@Composable
private fun SheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
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
        title = { Text("تغییر ویدیو") },
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
            .clip(RoundedCornerShape(14.dp))
            .background(if (isMe) BrandPurple.copy(alpha = 0.22f) else BrandCard)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarImage(avatarId = p.avatar, size = 24.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            if (isMe) "${p.name} (من)" else p.name,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 90.dp)
        )
        if (p.isLeader) { Spacer(Modifier.width(4.dp)); Text("👑", fontSize = 12.sp) }
    }
}

@Composable
private fun MessageRow(m: WsMessage, isMe: Boolean) {
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
                        if (m.avatar.isNotBlank()) {
                            AvatarImage(avatarId = m.avatar, size = 14.dp)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(m.name, fontSize = 11.sp, color = if (isMe) Color.White.copy(0.85f) else BrandCyan, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(m.text, fontSize = 14.sp, color = if (isMe) Color.White else BrandText)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(m.ts)),
                    fontSize = 9.sp,
                    color = if (isMe) Color.White.copy(0.6f) else BrandTextMuted
                )
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
    onKick: (String) -> Unit,
    onMute: (String, Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(24.dp),
        containerColor = BrandCard,
        title = { Text("اعضای اتاق (${peers.size})") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(peers, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BrandCardLight).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarImage(avatarId = p.avatar, size = 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                if (p.isLeader) "مدیر اتاق" else "عضو",
                                fontSize = 11.sp,
                                color = if (p.isLeader) BrandAmber else BrandTextMuted
                            )
                        }
                        if (isHost && p.id != myId) {
                            IconButton(onClick = { onMute(p.id, true) }) {
                                Icon(
                                    Icons.Default.MicOff,
                                    contentDescription = "سکوت",
                                    tint = BrandTextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = { onKick(p.id); onClose() }) {
                                Icon(Icons.Default.Delete, "اخراج", tint = BrandDanger, modifier = Modifier.size(18.dp))
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

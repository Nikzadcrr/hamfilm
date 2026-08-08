package com.hamfilm.app.ui.screens

import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
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
    val lifecycleOwner = LocalLifecycleOwner.current

    // ---------- ExoPlayer ----------
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    // اتصال به اتاق
    var connected by remember { mutableStateOf(false) }
    LaunchedEffect(roomCode) {
        if (!connected) {
            connected = true
            vm.connect(
                code = roomCode,
                name = TokenStore.name.ifBlank { "مهمان" },
                avatar = TokenStore.avatar,
                password = initialPassword,
                videoUrl = initialVideoUrl
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

    // کنترل‌های راه دور → پلیر
    vm.onRemotePlay = { time -> player.playWhenReady = true }
    vm.onRemotePause = { time -> player.playWhenReady = false }
    vm.onRemoteSeek = { time -> if (time > 0) player.seekTo(time) }
    vm.onRemoteVideo = { url ->
        if (url.isNotBlank()) {
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
        }
    }

    // اکشن کاربر → ارسال به اتاق + پخش
    fun onUserPlay() { player.playWhenReady = true; vm.togglePlay(player.currentPosition) }
    fun onUserPause() { player.playWhenReady = false; vm.togglePlay(player.currentPosition) }
    fun onUserSeek(pos: Long) { player.seekTo(pos); vm.seekTo(pos) }

    // دنبال کردن موقعیت پخش
    LaunchedEffect(player) {
        while (true) {
            delay(500)
            if (player.duration > 0) {
                vm.updatePosition(player.currentPosition, player.duration)
            }
        }
    }

    // وضعیت‌های UI
    var chatOpen by remember { mutableStateOf(false) }
    var membersOpen by remember { mutableStateOf(false) }
    var chatText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var typingJob by remember { mutableStateOf<Job?>(null) }

    val peers by vm.peers.collectAsState()
    val messages by vm.messages.collectAsState()
    val typing by vm.typing.collectAsState()
    val socketState by vm.socketState.collectAsState()

    // اسکرول خودکار چت
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    GradientBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // ---------- هدر اتاق ----------
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.disconnect(); nav.popBackStack() }) {
                    Icon(Icons.Default.ArrowForward, "خروج", tint = BrandTextMuted)
                }
                Column(Modifier.weight(1f)) {
                    Text(vm.roomName.ifBlank { "اتاق $roomCode" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("کد: $roomCode", fontSize = 12.sp, color = BrandCyan, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        when (socketState) {
                            is SocketState.Connected -> StatusBar("متصل", BrandGreen, Modifier)
                            is SocketState.Connecting -> StatusBar("در حال اتصال…", BrandAmber, Modifier)
                            else -> StatusBar("قطع", BrandDanger, Modifier)
                        }
                    }
                }
                IconButton(onClick = { membersOpen = true }) {
                    BadgedBox(badge = {
                        if (peers.size > 0) Badge { Text("${peers.size}") }
                    }) {
                        Icon(Icons.Default.People, "اعضا", tint = BrandText)
                    }
                }
                IconButton(onClick = { chatOpen = true }) {
                    Icon(Icons.Default.ChatBubbleOutline, "چت", tint = BrandText)
                }
            }

            // ---------- پلیر ویدیو ----------
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
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

                // دکمه‌های کنترل همگام
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FloatingActionButton(
                        onClick = { if (player.isPlaying) onUserPause() else onUserPlay() },
                        modifier = Modifier.size(44.dp),
                        containerColor = BrandPurple,
                        shape = CircleShape
                    ) {
                        Icon(
                            if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }

                // ---------- واکنش‌های لحظه‌ای (Emoji Blast) ----------
                ReactionBurst(vm)
            }

            // ---------- نوار اعضا ----------
            if (peers.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    items(peers, key = { it.id }) { p ->
                        PeerChip(p, isMe = p.id == vm.myId)
                    }
                }
            }

            // ---------- پیش‌نمایش چت ----------
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages, key = { it.id }) { m -> MessageRow(m, isMe = m.userId == vm.myId) }
                if (typing.isNotEmpty()) {
                    item { Text("✍️ ${typing.size} نفر در حال تایپ…", fontSize = 12.sp, color = BrandTextMuted) }
                }
            }

            // ---------- ورودی چت ----------
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // واکنش‌های سریع
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(Emojis) { e ->
                        Text(
                            e,
                            fontSize = 20.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { vm.sendReaction(e) }
                                .padding(6.dp)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = chatText,
                    onValueChange = {
                        chatText = it
                        vm.notifyTyping(it.isNotBlank())
                        typingJob?.cancel()
                        if (it.isNotBlank()) {
                            typingJob = scope.launch {
                                delay(2000)
                                vm.notifyTyping(false)
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
                    onClick = {
                        vm.sendChat(chatText)
                        chatText = ""
                        vm.notifyTyping(false)
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = BrandPurple)
                ) {
                    Icon(Icons.Default.Send, "ارسال", tint = Color.White)
                }
            }
        }
    }

    // ---------- دیالوگ اعضا و کنترل میزبان ----------
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

    // ---------- اخراج شدن ----------
    if (vm.kickedOut) {
        AlertDialog(
            onDismissRequest = {},
            shape = RoundedCornerShape(24.dp),
            containerColor = BrandCard,
            title = { Text("از اتاق اخراج شدی", textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = { Text("مدیر اتاق تو را اخراج کرد.", color = BrandTextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                GradientButton("باشه", onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth())
            }
        )
    }
}

// ---------- انیمیشن واکنش (Emoji Blast) ----------
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

// ---------- چیپ عضو ----------
@Composable
private fun PeerChip(p: WsPeer, isMe: Boolean) {
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isMe) BrandGradientSoft else BrandCard)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(p.avatar, fontSize = 16.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            if (isMe) "${p.name} (من)" else p.name,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 90.dp)
        )
        if (p.isHost) {
            Spacer(Modifier.width(4.dp))
            Text("👑", fontSize = 12.sp)
        }
        if (p.muted) {
            Spacer(Modifier.width(4.dp))
            Text("🔇", fontSize = 12.sp)
        }
    }
}

// ---------- ردیف پیام ----------
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
                            topStart = 14.dp,
                            topEnd = 14.dp,
                            bottomStart = if (isMe) 14.dp else 4.dp,
                            bottomEnd = if (isMe) 4.dp else 14.dp
                        )
                    )
                    .background(if (isMe) BrandPurple.copy(alpha = 0.85f) else BrandCard)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(Modifier.widthIn(max = 260.dp)) {
                    Text(m.userName, fontSize = 11.sp, color = if (isMe) Color.White.copy(0.85f) else BrandCyan, fontWeight = FontWeight.Bold)
                    Text(m.text, fontSize = 14.sp, color = if (isMe) Color.White else BrandText)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(m.time)),
                    fontSize = 9.sp,
                    color = if (isMe) Color.White.copy(0.6f) else BrandTextMuted
                )
            }
        }
    }
}

// ---------- دیالوگ اعضا + کنترل میزبان ----------
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
                        Text(p.avatar, fontSize = 22.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(if (p.isHost) "مدیر اتاق" else if (p.muted) "سکوت شده" else "عضو", fontSize = 11.sp, color = if (p.isHost) BrandAmber else BrandTextMuted)
                        }
                        if (isHost && p.id != myId) {
                            IconButton(onClick = { onMute(p.id, !p.muted) }) {
                                Icon(
                                    if (p.muted) Icons.Default.Mic else Icons.Default.MicOff,
                                    contentDescription = "سکوت",
                                    tint = if (p.muted) BrandGreen else BrandTextMuted,
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

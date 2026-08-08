package com.hamfilm.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hamfilm.app.data.TokenStore
import com.hamfilm.app.data.api.AppRepository
import com.hamfilm.app.data.model.CreateRoomRequest
import com.hamfilm.app.data.model.RoomInfo
import com.hamfilm.app.data.ws.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RoomViewModel : ViewModel() {

    private val repo = AppRepository()

    var socket: RoomSocket? = null
        private set

    var roomCode by mutableStateOf("")
        private set
    var roomName by mutableStateOf("")
        private set
    var connectionError by mutableStateOf<String?>(null)
    var kickedOut by mutableStateOf(false)
    var mutedInChat by mutableStateOf(false)
        private set

    val peers = MutableStateFlow<List<WsPeer>>(emptyList())
    val messages = MutableStateFlow<List<WsMessage>>(emptyList())
    val typing = MutableStateFlow<Set<String>>(emptySet())
    val unread = MutableStateFlow(0)
    var lastReadCount by mutableStateOf(0)
        private set
    val reads = MutableStateFlow<Map<String, Long>>(emptyMap())
    val socketState = MutableStateFlow<SocketState>(SocketState.Idle)

    // پخش هم‌زمان (منبع حقیقت: سرور)
    var videoUrl by mutableStateOf("")
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableStateOf(0L)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var playbackSpeed by mutableStateOf(1.0)
        private set
    var controlMode by mutableStateOf("host")
        private set
    var roomLocked by mutableStateOf(false)
        private set

    // واکنش‌های لحظه‌ای
    val reactions = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 32)

    // فایل محلی در حال پخش (از حافظه گوشی)
    var localFileName by mutableStateOf<String?>(null)
        private set
    val fileInfo = MutableStateFlow<WsFileInfo?>(null)

    val myId: String get() = socket?.myId ?: ""
    val isHost: Boolean get() = socket?.isHost ?: false

    // ---------- ساخت / ورود به اتاق ----------
    fun createRoom(name: String, videoUrl: String, password: String, avatar: String, onDone: (RoomInfoResult) -> Unit) {
        viewModelScope.launch {
            try {
                val room = repo.createRoom(CreateRoomRequest(name, videoUrl, password, avatar))
                roomCode = room.id
                onDone(RoomInfoResult.Success(room.id, room.hasPassword))
            } catch (e: Exception) {
                onDone(RoomInfoResult.Error(e.message ?: "خطا"))
            }
        }
    }

    /** چک‌کردن اتاق قبل از ورود (برای رمزدار بودن) */
    fun checkRoom(code: String, onDone: (RoomInfoResult) -> Unit) {
        viewModelScope.launch {
            try {
                val room = repo.roomInfo(code.uppercase())
                if (room == null || room.id.isBlank()) {
                    onDone(RoomInfoResult.Error("اتاق پیدا نشد. کد را چک کنید."))
                } else {
                    roomCode = room.id
                    onDone(RoomInfoResult.Success(room.id, room.hasPassword))
                }
            } catch (e: Exception) {
                onDone(RoomInfoResult.Error(e.message ?: "اتاق پیدا نشد"))
            }
        }
    }

    sealed class RoomInfoResult {
        data class Success(val code: String, val hasPassword: Boolean) : RoomInfoResult()
        data class Error(val message: String) : RoomInfoResult()
    }

    /** اتصال WebSocket به اتاق */
    fun connect(code: String, name: String, avatar: String, password: String, initialVideoUrl: String = "") {
        roomCode = code
        this.videoUrl = initialVideoUrl
        val s = RoomSocket(code).also {
            socket = it
            it.connect(name, avatar, password)
        }
        viewModelScope.launch {
            s.state.collect { st ->
                socketState.value = st
                if (st is SocketState.Error && socketState.value != SocketState.Connected) {
                    connectionError = st.message
                }
            }
        }
        viewModelScope.launch { s.peers.collect { peers.value = it } }
        viewModelScope.launch {
            s.messages.collect { list ->
                val prev = messages.value
                messages.value = list
                // شمارش پیام‌های جدید دیگران
                if (list.size > prev.size) {
                    val newMsgs = list.drop(prev.size).filter { !it.system && it.senderId != myId }
                    if (newMsgs.isNotEmpty()) unread.value = unread.value + newMsgs.size
                }
            }
        }
        viewModelScope.launch { s.typing.collect { typing.value = it } }
        viewModelScope.launch { s.roomInfo.collect { info -> info?.let { roomName = it.optString("name", roomName) } } }
        viewModelScope.launch { s.reactions.collect { reactions.emit(it) } }
        viewModelScope.launch { s.fileInfo.collect { fileInfo.value = it } }

        // وضعیت پخش از سرور
        viewModelScope.launch {
            s.playback.collect { p ->
                isPlaying = p.playing
                playbackSpeed = p.speed
                if (p.timeSec > 0) positionMs = (p.timeSec * 1000).toLong()
                onRemoteState?.invoke(p.playing, p.timeSec, p.speed)
            }
        }
        // اصلاح همگام (وقتی اختلاف زیاد است)
        viewModelScope.launch {
            s.correct.collect { c ->
                positionMs = (c.timeSec * 1000).toLong()
                onRemoteCorrect?.invoke(c.timeSec, c.playing)
            }
        }
        // تغییر ویدیو
        viewModelScope.launch {
            s.videoUrl.collect { url ->
                if (url.isNotBlank() && url != videoUrl) {
                    videoUrl = url
                    localFileName = null
                    onRemoteVideo?.invoke(url)
                }
            }
        }
        // قفل / کنترل
        viewModelScope.launch { s.locked.collect { roomLocked = it } }
        viewModelScope.launch { s.controlMode.collect { controlMode = it } }
        // اخراج / سکوت
        viewModelScope.launch {
            s.kicked.collect {
                kickedOut = true
                disconnect()
            }
        }
        viewModelScope.launch { s.muted.collect { mutedInChat = it } }
        viewModelScope.launch { s.reads.collect { reads.value = it } }
    }

    // کال‌بک‌هایی که پلیر (ExoPlayer) به ViewModel می‌دهد
    var onRemoteState: ((playing: Boolean, timeSec: Double, speed: Double) -> Unit)? = null
    var onRemoteCorrect: ((timeSec: Double, playing: Boolean) -> Unit)? = null
    var onRemoteVideo: ((String) -> Unit)? = null

    // ---------- اکشن‌های کاربر ----------
    fun sendChat(text: String) {
        if (text.isBlank()) return
        socket?.sendChat(text.trim())
    }

    fun sendReaction(emoji: String) {
        // فقط در چت ارسال می‌شود (بدون واکنش شناور روی صفحه)
        socket?.sendChat(emoji)
    }

    fun notifyTyping(on: Boolean) {
        socket?.sendTyping(on)
    }

    /**
     * ارسال فرمان پخش/توقف به سرور — وضعیت دقیقاً همان چیزی است که کاربر خواسته.
     * (نسخه قدیمی دوباره وضعیت را برعکس می‌کرد و باعث می‌شد پخش → استپ شود)
     */
    fun setPlayback(playing: Boolean, currentPosMs: Long) {
        if (!canControl) return
        if (playing) socket?.sendPlay(currentPosMs) else socket?.sendPause(currentPosMs)
    }

    fun seekTo(posMs: Long) {
        positionMs = posMs
        socket?.sendSeek(posMs)
    }

    fun setSpeed(rate: Double) {
        playbackSpeed = rate
        socket?.sendSpeed(rate)
    }

    fun changeVideo(url: String) {
        videoUrl = url
        localFileName = null
        socket?.sendChangeVideo(url)
    }

    /** پخش فایل از حافظه گوشی + اعلام به بقیه */
    fun shareLocalFile(name: String, size: Long, hash: String = "") {
        localFileName = name
        socket?.sendSetLocalFile(name, size, hash)
        socket?.sendPlay(0)
    }

    fun rename(name: String) {
        socket?.sendRename(name)
        TokenStore.name = name
    }

    fun kick(userId: String) = socket?.kick(userId)
    fun mute(userId: String, muted: Boolean) = socket?.mute(userId, muted)
    fun lock(locked: Boolean) {
        socket?.lock(locked)
        roomLocked = locked
    }

    fun updatePosition(pos: Long, dur: Long) {
        positionMs = pos
        durationMs = dur
    }

    val canControl: Boolean get() = isHost || controlMode == "all"

    /** وضعیت پخش محلی پلیر (برای آیکون درست) — بدون ارسال به سرور */
    fun onLocalPlayStateChange(playing: Boolean) {
        isPlaying = playing
    }

    /**
     * اتصال داده‌های زنده پلیر به پینگ همگام‌سازی.
     * هر ۱۵ ثانیه موقعیت واقعی پخش به سرور می‌رود تا (۱) کاربر از لیست اعضا حذف نشود
     * و (۲) اگر از بقیه عقب/جلو افتاد، سرور پیام correct بفرستد.
     */
    fun bindPlayerToPing(playingProvider: () -> Boolean, positionProvider: () -> Long) {
        socket?.playbackSnapshot = { playingProvider() to positionProvider() }
    }

    fun markChatRead() {
        unread.value = 0
        // اعلام رسید مطالعه به سرور (تیک دیده‌شدن برای بقیه)
        val last = messages.value.lastOrNull { !it.system }
        if (last != null && last.senderId != myId) {
            socket?.sendRead(last.ts)
        }
    }

    /** تیک دیده‌شدن: آیا پیام با این ts توسط حداقل یک نفر دیده شده؟ */
    fun isSeenByOthers(ts: Long): Boolean {
        return reads.value.values.any { it >= ts }
    }

    fun disconnect() {
        socket?.disconnect()
    }

    override fun onCleared() {
        socket?.disconnect()
        super.onCleared()
    }
}

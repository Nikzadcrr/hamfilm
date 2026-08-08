package com.hamfilm.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hamfilm.app.data.TokenStore
import com.hamfilm.app.data.api.ApiClient
import com.hamfilm.app.data.api.AppRepository
import com.hamfilm.app.data.model.CreateRoomRequest
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

    val peers = MutableStateFlow<List<WsPeer>>(emptyList())
    val messages = MutableStateFlow<List<WsMessage>>(emptyList())
    val typing = MutableStateFlow<Set<String>>(emptySet())
    val socketState = MutableStateFlow<SocketState>(SocketState.Idle)

    // پخش هم‌زمان
    var videoUrl by mutableStateOf("")
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableStateOf(0L)
        private set
    var durationMs by mutableStateOf(0L)
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
                roomCode = room.code
                onDone(RoomInfoResult.Success(room.code, room.password))
            } catch (e: Exception) {
                onDone(RoomInfoResult.Error(e.message ?: "خطا"))
            }
        }
    }

    sealed class RoomInfoResult {
        data class Success(val code: String, val password: String) : RoomInfoResult()
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
        viewModelScope.launch { s.messages.collect { messages.value = it } }
        viewModelScope.launch { s.typing.collect { typing.value = it } }
        viewModelScope.launch { s.roomInfo.collect { info -> info?.let { roomName = it.optString("name", roomName) } } }
        viewModelScope.launch { s.reactions.collect { reactions.emit(it) } }
        viewModelScope.launch { s.fileInfo.collect { fileInfo.value = it } }
        viewModelScope.launch {
            s.kicked.collect {
                kickedOut = true
                disconnect()
            }
        }
        // کنترل پخش از سرور
        viewModelScope.launch {
            s.control.collect { c ->
                when (c.mode) {
                    "play" -> { isPlaying = true; onRemotePlay?.invoke(c.timeMs) }
                    "pause" -> { isPlaying = false; onRemotePause?.invoke(c.timeMs) }
                    "seek" -> { onRemoteSeek?.invoke(c.timeMs) }
                    "video" -> { videoUrl = c.url; isPlaying = true; onRemoteVideo?.invoke(c.url) }
                }
            }
        }
    }

    // کال‌بک‌هایی که پلیر (ExoPlayer) به ViewModel می‌دهد
    var onRemotePlay: ((Long) -> Unit)? = null
    var onRemotePause: ((Long) -> Unit)? = null
    var onRemoteSeek: ((Long) -> Unit)? = null
    var onRemoteVideo: ((String) -> Unit)? = null

    // ---------- اکشن‌های کاربر ----------
    fun sendChat(text: String) {
        if (text.isBlank()) return
        socket?.sendChat(text.trim())
    }

    fun sendReaction(emoji: String) {
        socket?.sendReaction(emoji)
        viewModelScope.launch { reactions.emit("شما" to emoji) }
    }

    fun notifyTyping(on: Boolean) {
        socket?.sendTyping(on)
    }

    fun togglePlay(currentPos: Long) {
        isPlaying = !isPlaying
        socket?.sendControl(if (isPlaying) "play" else "pause", currentPos)
    }

    fun seekTo(pos: Long) {
        positionMs = pos
        socket?.sendControl("seek", pos)
    }

    fun changeVideo(url: String) {
        videoUrl = url
        localFileName = null
        socket?.sendControl("video", 0L, url)
    }

    /** پخش فایل از حافظه گوشی + اعلام به بقیه */
    fun shareLocalFile(name: String, size: Long) {
        localFileName = name
        socket?.sendFile(name, size)
        socket?.sendControl("play", 0L)
    }

    fun rename(name: String) {
        socket?.sendRename(name)
        TokenStore.name = name
    }

    fun kick(userId: String) = socket?.kick(userId)
    fun mute(userId: String, muted: Boolean) = socket?.mute(userId, muted)
    fun lock(locked: Boolean) = socket?.lock(locked)

    fun updatePosition(pos: Long, dur: Long) {
        positionMs = pos
        durationMs = dur
    }

    fun disconnect() {
        socket?.disconnect()
    }

    override fun onCleared() {
        socket?.disconnect()
        super.onCleared()
    }
}

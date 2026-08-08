package com.hamfilm.app.data.ws

import com.hamfilm.app.data.ApiConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import org.json.JSONObject

// ---------- مدل‌های بلادرنگ (مطابق پروتکل سایت هم‌فیلم) ----------
data class WsPeer(
    val id: String = "",
    val name: String = "",
    val avatar: String = "🎬",
    val isHost: Boolean = false,
    val muted: Boolean = false
)

data class WsMessage(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val avatar: String = "🎬",
    val text: String = "",
    val time: Long = 0L,
    val system: Boolean = false
)

data class PlaybackControl(
    val mode: String = "pause",   // play | pause | seek | video
    val timeMs: Long = 0L,
    val url: String = "",         // وقتی mode == video
    val byUserId: String = ""
)

sealed class SocketState {
    object Idle : SocketState()
    object Connecting : SocketState()
    object Connected : SocketState()
    data class Error(val message: String) : SocketState()
}

/**
 * کلاینت WebSocket اتاق — دقیقاً با همان پیام‌های سایت هم‌فیلم کار می‌کند:
 * join / leave / peers / chat / reaction / typing / control / system / presence / rename / kick / mute / ping
 */
class RoomSocket(
    private val roomCode: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var retries = 0

    // ---------- state ----------
    private val _state = MutableStateFlow<SocketState>(SocketState.Idle)
    val state: StateFlow<SocketState> = _state

    private val _peers = MutableStateFlow<List<WsPeer>>(emptyList())
    val peers: StateFlow<List<WsPeer>> = _peers

    private val _messages = MutableStateFlow<List<WsMessage>>(emptyList())
    val messages: StateFlow<List<WsMessage>> = _messages

    private val _control = MutableSharedFlow<PlaybackControl>(extraBufferCapacity = 16)
    val control: SharedFlow<PlaybackControl> = _control

    private val _reactions = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 32) // (userName, emoji)
    val reactions: SharedFlow<Pair<String, String>> = _reactions

    private val _typing = MutableStateFlow<Set<String>>(emptySet())
    val typing: StateFlow<Set<String>> = _typing

    private val _roomInfo = MutableStateFlow<JSONObject?>(null)
    val roomInfo: StateFlow<JSONObject?> = _roomInfo

    private val _kicked = MutableSharedFlow<Boolean>(extraBufferCapacity = 4)
    val kicked: SharedFlow<Boolean> = _kicked

    var myId: String = ""
        private set
    var isHost: Boolean = false
        private set

    private var name: String = ""
    private var avatar: String = "🎬"
    private var password: String = ""

    // ---------- اتصال ----------
    fun connect(name: String, avatar: String, password: String = "") {
        this.name = name
        this.avatar = avatar
        this.password = password
        _state.value = SocketState.Connecting
        openSocket()
    }

    private fun openSocket() {
        val url = ApiConfig.wsBase + roomCode
        val req = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(req, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            retries = 0
            _state.value = SocketState.Connected
            send(JSONObject().apply {
                put("type", "join")
                put("name", name)
                put("avatar", avatar)
                if (password.isNotBlank()) put("password", password)
            })
        }

        override fun onMessage(ws: WebSocket, text: String) {
            try {
                handleMessage(JSONObject(text))
            } catch (_: Exception) { }
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            scheduleReconnect()
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            _state.value = SocketState.Error(t.message ?: "خطای اتصال")
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (retries >= 8) return
        val delayMs = listOf(1L, 2L, 4L, 8L, 15L, 30L, 60L, 120L)[retries.coerceAtMost(7)] * 1000
        retries++
        scope.launch {
            delay(delayMs)
            if (_state.value is SocketState.Connected) return@launch
            _state.value = SocketState.Connecting
            openSocket()
        }
    }

    // ---------- دریافت ----------
    private fun handleMessage(json: JSONObject) {
        when (val type = json.optString("type")) {
            "room" -> _roomInfo.value = json
            "peers" -> {
                val list = json.optJSONArray("peers") ?: return
                val peers = (0 until list.length()).map { i ->
                    val p = list.getJSONObject(i)
                    WsPeer(
                        id = p.optString("id"),
                        name = p.optString("name"),
                        avatar = p.optString("avatar", "🎬"),
                        isHost = p.optBoolean("isHost"),
                        muted = p.optBoolean("muted")
                    )
                }
                if (myId.isBlank() && peers.isNotEmpty()) {
                    myId = peers.first().id
                }
                isHost = peers.any { it.id == myId && it.isHost }
                _peers.value = peers
            }
            "chat" -> {
                val msg = json.optJSONObject("msg") ?: return
                val user = msg.optJSONObject("user")
                _messages.update { list ->
                    (list + WsMessage(
                        id = msg.optString("id"),
                        userId = user?.optString("id") ?: "",
                        userName = user?.optString("name") ?: "",
                        avatar = user?.optString("avatar", "🎬") ?: "🎬",
                        text = msg.optString("text"),
                        time = msg.optLong("time")
                    )).takeLast(500)
                }
            }
            "system" -> {
                _messages.update { list ->
                    (list + WsMessage(
                        id = "sys-${System.currentTimeMillis()}",
                        text = json.optString("text", ""),
                        time = System.currentTimeMillis(),
                        system = true
                    )).takeLast(500)
                }
            }
            "reaction" -> {
                val emoji = json.optString("reaction", "❤️")
                val userName = json.optString("name", "")
                _reactions.tryEmit(userName to emoji)
            }
            "typing" -> {
                val id = json.optString("id")
                val on = json.optBoolean("on")
                _typing.update { set ->
                    if (on) set + id else set - id
                }
            }
            "control" -> {
                _control.tryEmit(PlaybackControl(
                    mode = json.optString("mode", "pause"),
                    timeMs = json.optLong("time", 0L),
                    url = json.optString("url", ""),
                    byUserId = json.optString("by", "")
                ))
            }
            "kicked" -> {
                if (json.optBoolean("you", false)) _kicked.tryEmit(true)
            }
            "presence" -> { /* برای همگام‌سازی فایل محلی — فاز ۲ */ }
            "ping" -> send(JSONObject().put("type", "pong"))
        }
    }

    // ---------- ارسال ----------
    fun send(json: JSONObject) {
        webSocket?.send(json.toString())
    }

    fun sendChat(text: String) {
        send(JSONObject().apply {
            put("type", "chat")
            put("text", text)
        })
    }

    fun sendReaction(emoji: String) {
        send(JSONObject().apply {
            put("type", "reaction")
            put("reaction", emoji)
        })
    }

    fun sendTyping(on: Boolean) {
        send(JSONObject().apply {
            put("type", "typing")
            put("on", on)
        })
    }

    fun sendControl(mode: String, timeMs: Long = 0L, url: String = "") {
        send(JSONObject().apply {
            put("type", "control")
            put("mode", mode)
            put("time", timeMs)
            if (url.isNotBlank()) put("url", url)
        })
    }

    fun sendRename(newName: String) {
        send(JSONObject().apply {
            put("type", "rename")
            put("name", newName)
        })
    }

    fun sendPresence() {
        send(JSONObject().apply {
            put("type", "presence")
            put("rtt", 0)
            put("hasFile", false)
        })
    }

    fun kick(userId: String) {
        send(JSONObject().apply {
            put("type", "kick")
            put("id", userId)
        })
    }

    fun mute(userId: String, muted: Boolean) {
        send(JSONObject().apply {
            put("type", "mute")
            put("id", userId)
            put("muted", muted)
        })
    }

    fun lock(locked: Boolean) {
        send(JSONObject().apply {
            put("type", "lock")
            put("locked", locked)
        })
    }

    fun disconnect() {
        retries = 999
        webSocket?.close(1000, "bye")
        webSocket = null
        _state.value = SocketState.Idle
    }
}

package com.hamfilm.app.data.ws

import com.hamfilm.app.data.ApiConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import org.json.JSONObject

// ---------- مدل‌های بلادرنگ (مطابق پروتکل بک‌اند اصلی هم‌فیلم) ----------
data class WsPeer(
    val id: String = "",
    val name: String = "",
    val avatar: String = "",          // id آواتار (a1..a10)
    val isLeader: Boolean = false,    // بک‌اند: isLeader
    val joinedAt: Long = 0,
    val lastSeen: Long = 0,
    val rtt: Long? = null,
    val hasFile: Boolean? = null
)

data class WsMessage(
    val id: String = "",
    val senderId: String = "",        // برای تشخیص «پیام خودم»
    val name: String = "",
    val avatar: String = "",
    val text: String = "",
    val ts: Long = 0,
    val system: Boolean = false
)

/** وضعیت پخش هماهنگ — بک‌اند: state / correct */
data class PlaybackState(
    val playing: Boolean = false,
    val timeSec: Double = 0.0,
    val updatedAt: Long = 0,
    val speed: Double = 1.0,
    val by: String = ""
)

/** اصلاح همگام (correct) — وقتی اختلاف با سرور زیاد است */
data class CorrectState(
    val timeSec: Double = 0.0,
    val playing: Boolean = false,
    val updatedAt: Long = 0
)

/** فایل محلی در حال پخش (بک‌اند: localFile) */
data class WsFileInfo(
    val name: String = "",
    val size: Long = 0,
    val hash: String = ""
)

sealed class SocketState {
    object Idle : SocketState()
    object Connecting : SocketState()
    object Connected : SocketState()
    data class Error(val message: String) : SocketState()
}

/**
 * کلاینت WebSocket اتاق — دقیقاً با پروتکل بک‌اند اصلی:
 * ارسال: join / play / pause / seek / speed / chat / reaction / typing /
 *        rename / changeVideo / setLocalFile / presence / kick / mute / lock / ping
 * دریافت: room / history / peers / state / correct / chat / system /
 *         reaction / typing / video / localFile / kicked / muted / control /
 *         lock / pong / error
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

    /** وضعیت پخش هماهنگ از سرور */
    private val _playback = MutableStateFlow(PlaybackState())
    val playback: StateFlow<PlaybackState> = _playback

    private val _correct = MutableSharedFlow<CorrectState>(extraBufferCapacity = 8)
    val correct: SharedFlow<CorrectState> = _correct

    private val _videoUrl = MutableStateFlow("")
    val videoUrl: StateFlow<String> = _videoUrl

    private val _reactions = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 32)
    val reactions: SharedFlow<Pair<String, String>> = _reactions

    private val _typing = MutableStateFlow<Set<String>>(emptySet())
    val typing: StateFlow<Set<String>> = _typing

    private val _roomInfo = MutableStateFlow<JSONObject?>(null)
    val roomInfo: StateFlow<JSONObject?> = _roomInfo

    private val _fileInfo = MutableStateFlow<WsFileInfo?>(null)
    val fileInfo: StateFlow<WsFileInfo?> = _fileInfo

    private val _kicked = MutableSharedFlow<Boolean>(extraBufferCapacity = 4)
    val kicked: SharedFlow<Boolean> = _kicked

    private val _muted = MutableSharedFlow<Boolean>(extraBufferCapacity = 4)
    val muted: SharedFlow<Boolean> = _muted

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked

    /** کنترل پخش: host | all */
    private val _controlMode = MutableStateFlow("host")
    val controlMode: StateFlow<String> = _controlMode

    var myId: String = ""
        private set
    var isHost: Boolean = false
        private set

    private var name: String = ""
    private var avatar: String = ""
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
            // ── ورود موفق: myId + وضعیت اولیه ──
            "room" -> {
                myId = json.optString("myId")
                val room = json.optJSONObject("room")
                _roomInfo.value = json
                room?.let {
                    _videoUrl.value = it.optString("videoUrl")
                }
                val state = json.optJSONObject("state")
                if (state != null) {
                    _playback.value = PlaybackState(
                        playing = state.optBoolean("playing"),
                        timeSec = state.optDouble("time"),
                        updatedAt = state.optLong("updatedAt"),
                        speed = state.optDouble("speed", 1.0)
                    )
                }
                val lf = json.optJSONObject("localFile")
                if (lf != null && lf.length() > 0) {
                    _fileInfo.value = WsFileInfo(lf.optString("name"), lf.optLong("size"), lf.optString("hash"))
                }
                _locked.value = json.optBoolean("locked")
                _controlMode.value = json.optString("control", "host")
            }

            // ── تاریخچه چت ──
            "history" -> {
                val arr = json.optJSONArray("messages") ?: return
                val list = (0 until arr.length()).mapNotNull { i ->
                    val m = arr.optJSONObject(i) ?: return@mapNotNull null
                    WsMessage(
                        id = m.optString("id"),
                        name = m.optString("name"),
                        avatar = m.optString("avatar"),
                        text = m.optString("text"),
                        ts = m.optLong("ts")
                    )
                }
                _messages.value = list
            }

            // ── لیست کاربران (isLeader) ──
            "peers" -> {
                val list = json.optJSONArray("peers") ?: return
                val peers = (0 until list.length()).map { i ->
                    val p = list.getJSONObject(i)
                    WsPeer(
                        id = p.optString("id"),
                        name = p.optString("name"),
                        avatar = p.optString("avatar"),
                        isLeader = p.optBoolean("isLeader"),
                        joinedAt = p.optLong("joinedAt"),
                        lastSeen = p.optLong("lastSeen"),
                        rtt = if (p.has("rtt") && !p.isNull("rtt")) p.optLong("rtt") else null,
                        hasFile = if (p.has("hasFile") && !p.isNull("hasFile")) p.optBoolean("hasFile") else null
                    )
                }
                isHost = peers.any { it.id == myId && it.isLeader }
                _peers.value = peers
            }

            // ── وضعیت پخش (play/pause/seek/speed) ──
            "state" -> {
                _playback.value = PlaybackState(
                    playing = json.optBoolean("playing"),
                    timeSec = json.optDouble("time"),
                    updatedAt = json.optLong("updatedAt"),
                    speed = json.optDouble("speed", 1.0),
                    by = json.optString("by")
                )
            }

            // ── اصلاح همگام ──
            "correct" -> {
                _correct.tryEmit(CorrectState(
                    timeSec = json.optDouble("time"),
                    playing = json.optBoolean("playing"),
                    updatedAt = json.optLong("updatedAt")
                ))
            }

            // ── پیام چت ──
            "chat" -> {
                val msg = json.optJSONObject("msg") ?: return
                _messages.update { list ->
                    (list + WsMessage(
                        id = msg.optString("id"),
                        senderId = msg.optString("senderId"),
                        name = msg.optString("name"),
                        avatar = msg.optString("avatar"),
                        text = msg.optString("text"),
                        ts = msg.optLong("ts")
                    )).takeLast(500)
                }
            }

            // ── پیام سیستم ──
            "system" -> {
                _messages.update { list ->
                    (list + WsMessage(
                        id = "sys-${System.currentTimeMillis()}",
                        text = json.optString("text", ""),
                        ts = json.optLong("ts", System.currentTimeMillis()),
                        system = true
                    )).takeLast(500)
                }
            }

            // ── واکنش شناور (reaction: {emoji, from}) ──
            "reaction" -> {
                val r = json.optJSONObject("reaction")
                val emoji = r?.optString("emoji") ?: json.optString("emoji", "❤️")
                val from = r?.optString("from") ?: json.optString("from", "")
                _reactions.tryEmit(from to emoji)
            }

            // ── در حال نوشتن ──
            "typing" -> {
                val id = json.optString("id")
                val on = json.optBoolean("on")
                _typing.update { set ->
                    if (on) set + id else set - id
                }
            }

            // ── تغییر ویدیو ──
            "video" -> {
                _videoUrl.value = json.optString("videoUrl")
                _fileInfo.value = null
            }

            // ── فایل محلی ──
            "localFile" -> {
                val f = json.optJSONObject("file")
                _fileInfo.value = if (f != null && f.length() > 0) {
                    WsFileInfo(f.optString("name"), f.optLong("size"), f.optString("hash"))
                } else null
            }

            // ── اخراج ──
            "kicked" -> {
                _kicked.tryEmit(true)
            }

            // ── سکوت چت ──
            "muted" -> {
                _muted.tryEmit(json.optBoolean("muted"))
            }

            // ── حالت کنترل (host|all) ──
            "control" -> {
                _controlMode.value = json.optString("mode", "host")
            }

            // ── قفل اتاق ──
            "lock" -> {
                _locked.value = json.optBoolean("locked")
            }

            // ── pong (برای پینگ) ──
            "pong" -> { /* برای اندازه‌گیری RTT — فعلاً لازم نیست */ }

            // ── خطا ──
            "error" -> {
                _state.value = SocketState.Error(json.optString("text", "خطا"))
            }

            // ── قابل نادیده‌گرفتن ──
            "reads", "pin", "slowmode", "host", "presence" -> { }
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
            put("emoji", emoji)
        })
    }

    fun sendTyping(on: Boolean) {
        send(JSONObject().apply {
            put("type", "typing")
            put("on", on)
        })
    }

    // ── پخش هماهنگ: بک‌اند play/pause/seek با زمان به ثانیه ──
    fun sendPlay(timeMs: Long) {
        send(JSONObject().apply {
            put("type", "play")
            put("time", timeMs / 1000.0)
        })
    }

    fun sendPause(timeMs: Long) {
        send(JSONObject().apply {
            put("type", "pause")
            put("time", timeMs / 1000.0)
        })
    }

    fun sendSeek(timeMs: Long) {
        send(JSONObject().apply {
            put("type", "seek")
            put("time", timeMs / 1000.0)
        })
    }

    fun sendSpeed(rate: Double) {
        send(JSONObject().apply {
            put("type", "speed")
            put("rate", rate)
        })
    }

    /** تغییر ویدیو برای همه — بک‌اند: changeVideo */
    fun sendChangeVideo(url: String) {
        send(JSONObject().apply {
            put("type", "changeVideo")
            put("videoUrl", url)
        })
    }

    /** اعلام فایل محلی — بک‌اند: setLocalFile {file:{name,size,hash}} */
    fun sendSetLocalFile(name: String, size: Long, hash: String = "") {
        send(JSONObject().apply {
            put("type", "setLocalFile")
            put("file", JSONObject().apply {
                put("name", name)
                put("size", size)
                put("hash", hash)
            })
        })
    }

    fun sendRename(newName: String) {
        send(JSONObject().apply {
            put("type", "rename")
            put("name", newName)
        })
    }

    fun sendPresence(rtt: Long = 0, hasFile: Boolean = false) {
        send(JSONObject().apply {
            put("type", "presence")
            put("rtt", rtt)
            put("hasFile", hasFile)
        })
    }

    fun sendPing() {
        send(JSONObject().apply {
            put("type", "ping")
            put("time", System.currentTimeMillis() / 1000.0)
            put("playing", _playback.value.playing)
            put("t", System.currentTimeMillis())
            put("buffering", false)
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

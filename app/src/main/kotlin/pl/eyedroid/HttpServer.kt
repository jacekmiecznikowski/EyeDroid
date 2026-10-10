package pl.eyedroid

import android.content.Context
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.time.Duration.Companion.seconds

class HttpServer(context: Context, private val host: String, private val port: Int, private val token: String) {
    private val html = context.assets.open("receiver.html").bufferedReader().use { it.readText() }
    private val lifecycleLock = Mutex()
    private val clients = Semaphore(4)
    private var closed = false
    private val server = embeddedServer(CIO, host = host, port = port) {
        install(WebSockets) { pingPeriod = 15.seconds; timeout = 20.seconds; maxFrameSize = 4096 }
        routing {
            get("/") {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.response.header("Referrer-Policy", "no-referrer")
                call.response.header("X-Content-Type-Options", "nosniff")
                call.response.header("X-Frame-Options", "DENY")
                call.respondText(html, ContentType.Text.Html)
            }
            get("/health") { call.respondText("EyeDroid") }
            get("/api/status") {
                if (!authorized(call)) return@get
                val s = StreamState.session.value
                val c = StreamState.config.value
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.respondText(JSONObject().put("running", s.running).put("fps", s.fps)
                    .put("size", s.frameSize).put("encodeMs", s.encodeMs).put("connections", s.clients).put("audio", c.audio)
                    .put("front", c.front).put("torch", c.torch).put("quality", c.quality)
                    .put("fpsLimit", c.fps).put("error", s.error ?: JSONObject.NULL).toString(), ContentType.Application.Json)
            }
            // Query-only POST: never reads an unbounded request body. State-changing GET is forbidden.
            post("/api/config") {
                if (!authorized(call, control = true)) return@post
                val q = call.request.queryParameters
                try {
                    require(q.names().all { it in setOf("width", "height", "fps", "quality", "front", "torch") })
                    require(q.names().all { q.getAll(it)?.size == 1 })
                    fun int(name: String, old: Int) = q[name]?.let { requireNotNull(it.toIntOrNull()) } ?: old
                    fun bool(name: String, old: Boolean) = q[name]?.let { requireNotNull(it.toBooleanStrictOrNull()) } ?: old
                    StreamState.config.update { old ->
                        old.copy(width = int("width", old.width), height = int("height", old.height),
                            fps = int("fps", old.fps), quality = int("quality", old.quality),
                            front = bool("front", old.front), torch = bool("torch", old.torch)).validated()
                    }
                    call.respondText("Configuration queued; check /api/status", status = HttpStatusCode.Accepted)
                } catch (_: IllegalArgumentException) {
                    call.respondText("Invalid configuration", status = HttpStatusCode.BadRequest)
                }
            }
            get("/video.mjpg") {
                if (!authorized(call)) return@get
                if (!clients.tryAcquire()) {
                    call.respond(HttpStatusCode.ServiceUnavailable); return@get
                }
                connected(1)
                try {
                    call.response.header(HttpHeaders.CacheControl, "no-store, no-cache")
                    call.respondOutputStream(ContentType.parse("multipart/x-mixed-replace; boundary=eyedroid")) {
                        // StateFlow conflates JPEGs: one latest frame for each slow client.
                        StreamState.frames.filterNotNull().collect { frame ->
                            withTimeout(5000) {
                                write(("--eyedroid\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.bytes.size}\r\n\r\n")
                                    .toByteArray(Charsets.US_ASCII))
                                write(frame.bytes); write(byteArrayOf(13, 10)); flush()
                            }
                        }
                    }
                } finally { clients.release(); connected(-1) }
            }
            webSocket("/audio") {
                // A stream URL token is accepted because browser WebSocket cannot set Authorization.
                if (!validToken(call, false) || !validOrigin(call) || !StreamState.config.value.audio) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized or microphone disabled"))
                    return@webSocket
                }
                if (!clients.tryAcquire()) {
                    close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Client limit")); return@webSocket
                }
                connected(1)
                try {
                    send(Frame.Text("{\"format\":\"s16le\",\"sampleRate\":48000,\"channels\":1}"))
                    coroutineScope {
                        val sender = launch {
                            StreamState.audio.collect { pcm -> withTimeout(5000) { send(Frame.Binary(true, pcm)) } }
                        }
                        try { for (frame in incoming) { /* Capture is one-way; consume control input. */ } }
                        finally { sender.cancelAndJoin() }
                    }
                } finally { clients.release(); connected(-1) }
            }
        }
    }

    private fun validOrigin(call: ApplicationCall): Boolean {
        val origin = call.request.header(HttpHeaders.Origin) ?: return true
        return origin == "http://$host:$port"
    }
    private fun validToken(call: ApplicationCall, control: Boolean): Boolean {
        val header = call.request.header(HttpHeaders.Authorization)?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")
        val candidate = header ?: if (!control) call.request.queryParameters["token"] else null
        return candidate != null && candidate.length == token.length &&
            MessageDigest.isEqual(candidate.toByteArray(), token.toByteArray())
    }
    private suspend fun authorized(call: ApplicationCall, control: Boolean = false): Boolean {
        if (!validOrigin(call) || !validToken(call, control)) {
            call.respond(HttpStatusCode.Unauthorized); return false
        }
        return true
    }
    private fun connected(delta: Int) { StreamState.session.update { it.copy(clients = (it.clients + delta).coerceAtLeast(0)) } }
    suspend fun start() = withContext(Dispatchers.IO) {
        lifecycleLock.withLock { check(!closed); server.start(wait = false) }
    }
    suspend fun stop() = withContext(Dispatchers.IO) {
        lifecycleLock.withLock { closed = true; server.stop(0, 1000) }
    }
}

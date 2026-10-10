package pl.eyedroid

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Immutable configurations; all external input is checked before application. */
data class StreamConfig(
    val width: Int = 640, val height: Int = 480, val fps: Int = 30,
    val quality: Int = 75, val front: Boolean = false, val torch: Boolean = false,
    val audio: Boolean = true, val port: Int = 4747,
) {
    fun validated(): StreamConfig = apply {
        require(width to height in resolutions) { "Unsupported resolution" }
        require(fps in frameRates) { "Unsupported FPS" }
        require(quality in 40..90) { "JPEG quality must be 40..90" }
        require(port in 1024..65535) { "Port must be 1024..65535" }
        require(!(front && torch)) { "Torch requires the rear camera" }
    }
    companion object {
        val resolutions = listOf(640 to 480, 1280 to 720, 1920 to 1080)
        val frameRates = listOf(15, 24, 30)
    }
}

data class SessionState(
    val running: Boolean = false, val starting: Boolean = false,
    val url: String = "", val token: String = "", val clients: Int = 0,
    val fps: Int = 0, val encodeMs: Int = 0, val jpegKb: Int = 0, val frameSize: String = "—", val hasTorch: Boolean = false,
    val error: String? = null,
)
data class JpegFrame(val bytes: ByteArray, val timestampNs: Long)

/** Process-local media bus, never retains an Activity or an ImageProxy. */
object StreamState {
    val config = MutableStateFlow(StreamConfig())
    val pro = MutableStateFlow(false)
    val session = MutableStateFlow(SessionState())
    val frames = MutableStateFlow<JpegFrame?>(null)
    // PCM: signed 16-bit LE, mono, 48 kHz, one message per ~20 ms. No replay.
    val audio = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST)
    fun fail(message: String) { session.update { it.copy(error = message) } }
}

package pl.eyedroid

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.SystemClock
import android.util.Size
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.update
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraEngine(private val context: Context, private val owner: LifecycleOwner) {
    private val executor = Executors.newSingleThreadExecutor()
    private val generation = AtomicInteger()
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var camera: Camera? = null
    private var applied: StreamConfig? = null
    @Volatile private var config = StreamConfig()
    @Volatile private var closed = false

    suspend fun initialize() {
        val future = ProcessCameraProvider.getInstance(context)
        provider = suspendCancellableCoroutine { continuation ->
            future.addListener({
                if (continuation.isActive) try { continuation.resume(future.get()) }
                catch (e: Exception) { continuation.resumeWithException(e) }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    /** Main thread only. Resolution/lens rebind; FPS/JPEG changes are atomic encoder settings. */
    fun configure(next: StreamConfig) {
        check(!closed)
        next.validated()
        val old = applied
        if (old == null || next.width != old.width || next.height != old.height || next.front != old.front || next.fps != old.fps) {
            val p = checkNotNull(provider)
            val selector = if (next.front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            require(p.hasCamera(selector)) { "Wybrana kamera jest niedostępna" }
            val builder = ImageAnalysis.Builder()
            // Select an AE FPS range supported by this lens. A capture-rate cap alone
            // does not ask the camera sensor to deliver 30 frames each second.
            val cameraInfo = selector.filter(p.availableCameraInfos).first()
            val cameraId = Camera2CameraInfo.from(cameraInfo).cameraId
            val characteristics = context.getSystemService(CameraManager::class.java)
                .getCameraCharacteristics(cameraId)
            val supported = characteristics.get(
                CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
            )?.toList().orEmpty()
            val selected = supported.filter { it.lower <= next.fps && it.upper >= next.fps }
                .minWithOrNull(compareBy<Range<Int>>({ it.upper - it.lower }, { it.upper }))
            if (selected != null) {
                Camera2Interop.Extender(builder).setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, selected
                )
            }
            val useCase = builder
                .setTargetRotation(Surface.ROTATION_0)
                .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                    ResolutionStrategy(Size(next.width, next.height), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                ).build())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
            val epoch = generation.incrementAndGet()
            analysis?.let { it.clearAnalyzer(); p.unbind(it) }
            // Set before bind so partial startup can always release the owned use case.
            analysis = useCase
            camera = p.bindToLifecycle(owner, selector, useCase)
            val hasTorch = camera!!.cameraInfo.hasFlashUnit()
            StreamState.session.update { it.copy(hasTorch = hasTorch) }
            var last = 0L
            var count = 0
            var encodeTotalNs = 0L
            var window = SystemClock.elapsedRealtime()
            useCase.setAnalyzer(executor) { image ->
                try {
                    val settings = config
                    val now = SystemClock.elapsedRealtimeNanos()
                    // Camera frame timestamps jitter around nominal 30 Hz. Allow 2 ms slack
                    // so a frame at e.g. 33.0 ms is not skipped until 66 ms.
                    if ((last == 0L || now - last >= 1_000_000_000L / settings.fps - 2_000_000L) && !closed && epoch == generation.get()) {
                        last = now
                        val encodeStart = SystemClock.elapsedRealtimeNanos()
                        val frame = jpeg(image, settings.quality)
                        encodeTotalNs += SystemClock.elapsedRealtimeNanos() - encodeStart
                        if (!closed && epoch == generation.get()) {
                            StreamState.frames.value = JpegFrame(frame, now)
                            count++
                            val time = SystemClock.elapsedRealtime()
                            val rotation = image.imageInfo.rotationDegrees
                            val size = if (rotation % 180 == 0) "${image.cropRect.width()} × ${image.cropRect.height()}"
                                else "${image.cropRect.height()} × ${image.cropRect.width()}"
                            if (time - window >= 1000) {
                                val fps = (count * 1000L / (time - window)).toInt()
                                StreamState.session.update { it.copy(fps = fps, frameSize = size, encodeMs = if (count > 0) (encodeTotalNs / count / 1_000_000L).toInt() else 0) }
                                count = 0; encodeTotalNs = 0L; window = time
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!closed) StreamState.fail("Klatka kamery: ${e.message}")
                } finally { image.close() }
            }
        }
        require(!next.torch || camera?.cameraInfo?.hasFlashUnit() == true) { "Ta kamera nie ma lampy" }
        val torchFuture = camera?.cameraControl?.enableTorch(next.torch)
        torchFuture?.addListener({
            try { torchFuture.get() } catch (e: Exception) {
                if (!closed) StreamState.fail("Nie udało się zmienić lampy: ${e.cause?.message ?: e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
        config = next
        applied = next
    }

    private fun jpeg(image: ImageProxy, quality: Int): ByteArray {
        val crop = image.cropRect
        val width = crop.width() and -2
        val height = crop.height() and -2
        val packed = Yuv420.nv21(image.planes.map { Yuv420.Plane(it.buffer, it.rowStride, it.pixelStride) },
            crop.left and -2, crop.top and -2, width, height)
        val degrees = image.imageInfo.rotationDegrees
        val rotated = Yuv420.rotate(packed, width, height, degrees)
        val w = if (degrees % 180 == 0) width else height
        val h = if (degrees % 180 == 0) height else width
        return ByteArrayOutputStream(width * height / 4).use {
            check(YuvImage(rotated, ImageFormat.NV21, w, h, null)
                .compressToJpeg(Rect(0, 0, w, h), quality, it))
            it.toByteArray()
        }
    }

    fun close() {
        closed = true
        generation.incrementAndGet()
        analysis?.let { it.clearAnalyzer(); provider?.unbind(it) }
        analysis = null
        executor.shutdown()
    }
}

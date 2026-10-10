package pl.eyedroid

import java.nio.ByteBuffer

/** Stride-aware YUV_420_888 conversion. Pure Kotlin so padded planes can be tested on JVM. */
object Yuv420 {
    data class Plane(val buffer: ByteBuffer, val rowStride: Int, val pixelStride: Int)

    fun nv21(planes: List<Plane>, left: Int, top: Int, width: Int, height: Int): ByteArray {
        require(planes.size == 3 && width > 0 && height > 0)
        require(listOf(left, top, width, height).all { it % 2 == 0 })
        val out = ByteArray(width * height * 3 / 2)
        fun sample(p: Plane, x: Int, y: Int) = p.buffer.get(p.buffer.position() + y * p.rowStride + x * p.pixelStride)
        // Luma is usually tightly packed (pixelStride == 1), so copy each
        // cropped row in one bulk operation instead of 2 million byte reads at 1080p.
        val yPlane = planes[0]
        if (yPlane.pixelStride == 1) {
            val source = yPlane.buffer.duplicate()
            val base = source.position()
            for (y in 0 until height) {
                source.position(base + (top + y) * yPlane.rowStride + left)
                source.get(out, y * width, width)
            }
        } else {
            for (y in 0 until height) for (x in 0 until width) {
                out[y * width + x] = sample(yPlane, left + x, top + y)
            }
        }
        var i = width * height
        for (y in 0 until height / 2) for (x in 0 until width / 2) {
            out[i++] = sample(planes[2], left / 2 + x, top / 2 + y)
            out[i++] = sample(planes[1], left / 2 + x, top / 2 + y)
        }
        return out
    }

    /** Rotate luminance and VU pairs together, before JPEG encoding (no bitmap roundtrip). */
    fun rotate(src: ByteArray, width: Int, height: Int, degrees: Int): ByteArray {
        require(degrees in listOf(0, 90, 180, 270))
        if (degrees == 0) return src
        val out = ByteArray(src.size)
        fun rotatePlane(w: Int, h: Int, offset: Int, stride: Int) {
            val outW = if (degrees == 180) w else h
            for (y in 0 until h) for (x in 0 until w) {
                val dx = when (degrees) { 90 -> h - 1 - y; 180 -> w - 1 - x; else -> y }
                val dy = when (degrees) { 90 -> x; 180 -> h - 1 - y; else -> w - 1 - x }
                for (k in 0 until stride) out[offset + (dy * outW + dx) * stride + k] =
                    src[offset + (y * w + x) * stride + k]
            }
        }
        rotatePlane(width, height, 0, 1)
        rotatePlane(width / 2, height / 2, width * height, 2)
        return out
    }
}

package pl.eyedroid

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class Yuv420Test {
    @Test fun paddedPlanesAndInterleavedChromaRespectStrides() {
        val y = byteArrayOf(1,2,3,4,99,99,5,6,7,8,99,99)
        val u = byteArrayOf(20,99,21,99)
        val v = byteArrayOf(30,99,31,99)
        val actual = Yuv420.nv21(listOf(
            Yuv420.Plane(ByteBuffer.wrap(y), 6, 1),
            Yuv420.Plane(ByteBuffer.wrap(u), 4, 2),
            Yuv420.Plane(ByteBuffer.wrap(v), 4, 2)), 0, 0, 4, 2)
        assertArrayEquals(byteArrayOf(1,2,3,4,5,6,7,8,30,20,31,21), actual)
    }
    @Test fun rotationPreservesVuPairs() {
        val original = byteArrayOf(1,2,3,4,5,6,7,8,30,20,31,21)
        assertArrayEquals(byteArrayOf(5,1,6,2,7,3,8,4,30,20,31,21), Yuv420.rotate(original,4,2,90))
        assertArrayEquals(byteArrayOf(8,7,6,5,4,3,2,1,31,21,30,20), Yuv420.rotate(original,4,2,180))
        var rotated = original
        repeat(4) { i -> rotated = Yuv420.rotate(rotated, if(i%2==0) 4 else 2, if(i%2==0) 2 else 4,90) }
        assertArrayEquals(original, rotated)
    }
    @Test fun cropAndBufferPositionAreRespected() {
        val y = ByteBuffer.wrap(byteArrayOf(99,1,2,3,4,5,6,7,8)).apply { position(1) }
        val u = ByteBuffer.wrap(byteArrayOf(20,21))
        val v = ByteBuffer.wrap(byteArrayOf(30,31))
        assertArrayEquals(byteArrayOf(3,4,7,8,31,21), Yuv420.nv21(listOf(
            Yuv420.Plane(y,4,1),Yuv420.Plane(u,2,1),Yuv420.Plane(v,2,1)),2,0,2,2))
    }
}

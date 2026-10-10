package pl.eyedroid

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamConfigTest {
    @Test fun defaultsAreValid() { assertEquals(StreamConfig(), StreamConfig().validated()) }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsPrivilegedPort() { StreamConfig(port = 80).validated() }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnboundedResolution() { StreamConfig(width = 99999).validated() }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedFps() { StreamConfig(fps = 240).validated() }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsFrontTorch() { StreamConfig(front = true, torch = true).validated() }
}

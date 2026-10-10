package pl.eyedroid

import android.app.Application
import kotlinx.coroutines.*

/** Small local preferences only. No recordings, session keys, accounts or analytics are persisted. */
class EyeDroidApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override fun onCreate() {
        super.onCreate()
        val preferences = getSharedPreferences("studio", MODE_PRIVATE)
        StreamState.config.value = runCatching {
            StreamConfig(width = preferences.getInt("width", 1280), height = preferences.getInt("height", 720),
                fps = preferences.getInt("fps", 24), quality = preferences.getInt("quality", 75),
                front = preferences.getBoolean("front", false), audio = preferences.getBoolean("audio", true),
                port = preferences.getInt("port", 4747)).validated()
        }.getOrDefault(StreamConfig())
        scope.launch {
            StreamState.config.collect { c ->
                preferences.edit().putInt("width", c.width).putInt("height", c.height)
                    .putInt("fps", c.fps).putInt("quality", c.quality).putBoolean("front", c.front)
                    .putBoolean("audio", c.audio).putInt("port", c.port).apply()
            }
        }
    }
}

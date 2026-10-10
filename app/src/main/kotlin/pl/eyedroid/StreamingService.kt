package pl.eyedroid

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.update
import java.security.SecureRandom

class StreamingService : LifecycleService() {
    private var camera: CameraEngine? = null
    private var audio: AudioEngine? = null
    private var http: HttpServer? = null
    private var network: LocalNetwork? = null
    private var binding: LocalNetwork.Binding? = null
    private var wake: PowerManager.WakeLock? = null
    private var started = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        if (started) return START_NOT_STICKY
        if (cleanupJob?.isCompleted == false) { stopSelf(); return START_NOT_STICKY }
        started = true
        try {
            val c = StreamState.config.value.validated()
            val required = buildList {
                add(Manifest.permission.CAMERA)
                if (c.audio) add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= 37) add("android.permission.ACCESS_LOCAL_NETWORK")
            }
            check(required.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
                "Brak wymaganych uprawnień"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Transmisja", NotificationManager.IMPORTANCE_LOW))
            val types = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                (if (c.audio) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0) else 0
            ServiceCompat.startForeground(this, 1, notification("Uruchamianie kamery…"), types)
            StreamState.session.value = SessionState(starting = true)
            network = LocalNetwork(this) {
                lifecycleScope.launch {
                    val current = network?.binding()
                    if (binding != null && current != binding) {
                        StreamState.fail("Sieć Wi-Fi lub adres IP uległy zmianie. Uruchom sesję ponownie.")
                        stopSelf()
                    }
                }
            }
            binding = network!!.binding() ?: error("Połącz telefon z siecią Wi-Fi z adresem IPv4")
            network!!.start()
            val token = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            val url = "http://${binding!!.address}:${c.port}"
            wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EyeDroid:stream").apply {
                setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L)
            }
            camera = CameraEngine(this, this)
            http = HttpServer(this, binding!!.address, c.port, token)
            lifecycleScope.launch {
                try {
                    camera!!.initialize()
                    camera!!.configure(c)
                    if (c.audio) { audio = AudioEngine(lifecycleScope); audio!!.start() }
                    http!!.start()
                    StreamState.session.update { it.copy(running = true, starting = false, url = url, token = token) }
                    manager.notify(1, notification("Transmisja aktywna • $url"))
                    // Explicit six-hour session cap pairs with the bounded wake lock.
                    launch { delay(6 * 60 * 60 * 1000L); StreamState.fail("Sesja zakończona po 6 godzinach."); stopSelf() }
                    var applied = c
                    StreamState.config.collect { next ->
                        if (next != applied) try {
                            require(next.audio == c.audio && next.port == c.port) { "Zmień mikrofon/port przed uruchomieniem" }
                            camera!!.configure(next)
                            applied = next
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            StreamState.fail("Ustawienia: ${e.message}")
                            // Force a clean camera rebind on failed configuration.
                            camera?.close()
                            camera = CameraEngine(this@StreamingService, this@StreamingService)
                            camera!!.initialize(); camera!!.configure(applied)
                            StreamState.config.compareAndSet(next, applied)
                        }
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { StreamState.fail("Nie można uruchomić transmisji: ${e.message}"); stopSelf() }
            }
        } catch (e: Exception) { StreamState.fail(e.message ?: "Błąd uruchamiania"); stopSelf() }
        return START_NOT_STICKY
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, StreamingService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_eye)
            .setContentTitle("EyeDroid • kamera przez Wi-Fi").setContentText(text)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(0, "Zatrzymaj", stop).setCategory(NotificationCompat.CATEGORY_SERVICE).build()
    }

    override fun onDestroy() {
        // Cancel capture before clearing state; detached cleanup only releases session-owned resources.
        lifecycleScope.cancel()
        camera?.close()
        network?.close()
        val oldAudio = audio
        val oldHttp = http
        StreamState.session.update { it.copy(running = false, starting = true, token = "", url = "") }
        cleanupJob = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                try { oldAudio?.close() } finally { oldHttp?.stop() }
            } finally {
                StreamState.session.update { SessionState(error = it.error) }
            }
        }
        wake?.let { if (it.isHeld) it.release() }
        StreamState.frames.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object { private var cleanupJob: Job? = null; const val STOP = "pl.eyedroid.STOP"; private const val CHANNEL = "stream" }
}

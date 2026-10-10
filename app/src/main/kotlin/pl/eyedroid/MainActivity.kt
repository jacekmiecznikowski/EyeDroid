package pl.eyedroid

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

private fun t(pl: String, en: String): String = if (Locale.getDefault().language == "pl") pl else en

private val Lime = Color(0xFFA7F36B)
private val Base = Color(0xFF080D13)
private val Panel = Color(0xFF111A24)
private val Muted = Color(0xFF8D9CAB)
private val Line = Color(0xFF253340)

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        (applicationContext as EyeDroidApp).billing.refresh()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Lime, onPrimary = Base,
                background = Base, surface = Panel, onSurface = Color(0xFFF0F4F8),
                onSurfaceVariant = Muted, outline = Line)) { Dashboard() }
        }
    }
}

@Composable
private fun Dashboard() {
    val context = LocalContext.current
    val session by StreamState.session.collectAsStateWithLifecycle()
    val config by StreamState.config.collectAsStateWithLifecycle()
    val busy = session.running || session.starting
    val billing = remember { (context.applicationContext as EyeDroidApp).billing }
    val pro by StreamState.pro.collectAsStateWithLifecycle()
    val purchaseAvailable by billing.available.collectAsStateWithLifecycle()
    val billingMessage by billing.message.collectAsStateWithLifecycle()
    var showKey by remember { mutableStateOf(false) }
    fun launchStream() {
        runCatching { ContextCompat.startForegroundService(context, Intent(context, StreamingService::class.java)) }
            .onFailure { StreamState.fail("Uruchomienie: ${it.message}") }
    }
    fun required() = buildList {
        add(Manifest.permission.CAMERA)
        if (StreamState.config.value.audio) add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 37) add("android.permission.ACCESS_LOCAL_NETWORK")
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (required().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) launchStream()
        else StreamState.fail(t("Włącz uprawnienia kamery, mikrofonu i sieci lokalnej w ustawieniach telefonu.", "Enable camera, microphone and local-network permissions in your phone settings."))
    }
    fun copy(text: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("EyeDroid", text))
    }
    Surface(Modifier.fillMaxSize(), color = Base) {
        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Lime, shape = RoundedCornerShape(14.dp)) {
                    Text("◉", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Base, fontSize = 26.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("EyeDroid", fontSize = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                    Text("YOUR PHONE. YOUR STUDIO.", color = Muted, fontSize = 9.sp, letterSpacing = 2.sp)
                }
                Text("LOCAL\nONLY", color = Lime, fontSize = 10.sp, letterSpacing = 2.sp)
            }
            Column {
                Text(if (session.running) t("Jesteś na żywo.", "You're live.") else t("Twoje studio\nw kieszeni.", "Your studio\nin your pocket."),
                    fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.5).sp)
                Spacer(Modifier.height(8.dp))
                Text(t("Kamera przez Wi-Fi. Bez konta. Bez chmury.", "Wi-Fi webcam. No account. No cloud."), color = Muted, fontSize = 13.sp)
            }
            BoxWithConstraints {
                if (maxWidth > 760.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(Modifier.weight(1.25f)) { PreviewPanel(session, config) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            ConnectionPanel(session, showKey, { showKey = !showKey }, ::copy)
                            SettingsPanel(config, busy, session.hasTorch, pro)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        PreviewPanel(session, config)
                        ConnectionPanel(session, showKey, { showKey = !showKey }, ::copy)
                        SettingsPanel(config, busy, session.hasTorch, pro)
                    }
                }
            }
            Surface(color = Panel, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Line)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (pro) "EyeDroid Pro ✓" else "EyeDroid Pro", color = Lime, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(t("Odblokuj 720p i 1080p jednorazowym zakupem w Google Play.", "Unlock 720p and 1080p with a one-time Google Play purchase."), color = Muted, fontSize = 12.sp)
                    if (!pro) Button(onClick = { billing.buy(context as ComponentActivity) },
                        enabled = purchaseAvailable, modifier = Modifier.fillMaxWidth()) {
                        Text(if (purchaseAvailable) t("Kup pełną wersję", "Buy full version")
                            else t("Zakup dostępny po konfiguracji Google Play", "Available once Google Play is configured"))
                    }
                    billingMessage?.let { Text(it, color = Color(0xFFFFC8BE), fontSize = 11.sp) }
                }
            }
            session.error?.let { message ->
                Surface(color = Color(0xFF382321), shape = RoundedCornerShape(16.dp)) {
                    Text(message, Modifier.padding(16.dp), color = Color(0xFFFFC8BE), fontSize = 13.sp)
                }
            }
            Button(onClick = {
                if (session.running) context.stopService(Intent(context, StreamingService::class.java))
                else {
                    val permissions = required().toMutableList()
                    if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    permission.launch(permissions.toTypedArray())
                }
            }, enabled = !session.starting, shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (session.running) Color(0xFFFA795F) else Lime),
                modifier = Modifier.fillMaxWidth().height(62.dp)) {
                Text(if (session.starting) t("Przygotowywanie…", "Starting…") else if (session.running) t("■  Zatrzymaj transmisję", "■  Stop streaming") else t("●  Rozpocznij transmisję", "●  Start streaming"),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Text(t("Telefon i komputer muszą być w tej samej sieci Wi-Fi.\nTransmisja HTTP jest przeznaczona dla zaufanej sieci lokalnej.", "Phone and computer must be on the same Wi-Fi.\nHTTP streaming is for trusted local networks."),
                color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun PreviewPanel(session: SessionState, config: StreamConfig) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val preview by produceState<ImageBitmap?>(null, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            StreamState.frames.sample(160).collect { frame ->
                value = frame?.let { withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(it.bytes, 0, it.bytes.size)?.asImageBitmap()
                } }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionLabel(t("01 / PODGLĄD KAMERY", "01 / CAMERA PREVIEW"))
            Text(if (session.running) "● LIVE" else t("○ GOTOWOŚĆ", "○ READY"), color = if (session.running) Lime else Muted, fontSize = 11.sp)
        }
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF172C32), Color(0xFF101923), Base))),
            contentAlignment = Alignment.Center) {
            val bitmap = preview
            if (bitmap != null && session.running) Image(bitmap, "Podgląd transmitowanego obrazu",
                Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("◉", fontSize = 70.sp, color = Lime.copy(alpha = .6f))
                Text(t("KADR CZEKA NA CIEBIE", "CAMERA PREVIEW"), color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
            }
            Row(Modifier.align(Alignment.TopStart).padding(16.dp).background(Base.copy(alpha = .7f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                Text(if (config.front) "FRONT CAMERA" else "MAIN CAMERA", fontSize = 9.sp, letterSpacing = 1.sp)
            }
            Text("MJPEG / WI-FI", Modifier.align(Alignment.BottomStart).padding(16.dp)
                .background(Base.copy(alpha = .7f), RoundedCornerShape(8.dp)).padding(8.dp), fontSize = 9.sp, letterSpacing = 1.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("${session.fps}", "FPS / JPEG ${session.encodeMs}ms", Modifier.weight(1f))
            Metric(session.frameSize, t("ROZMIAR KLATKI", "FRAME SIZE"), Modifier.weight(1f))
            Metric("${session.clients}", t("POŁĄCZENIA", "CONNECTIONS"), Modifier.weight(1f))
        }
    }
}

@Composable
private fun Metric(value: String, caption: String, modifier: Modifier) {
    Surface(modifier, color = Panel, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(caption, fontSize = 8.sp, color = Muted, letterSpacing = .5.sp)
        }
    }
}

@Composable
private fun ConnectionPanel(session: SessionState, reveal: Boolean, toggle: () -> Unit, copy: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(t("02 / POŁĄCZ KOMPUTER", "02 / CONNECT COMPUTER"))
        Surface(shape = RoundedCornerShape(22.dp), color = Panel, border = BorderStroke(1.dp, Line)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (session.running) t("Otwórz adres w przeglądarce", "Open the address in your browser") else t("Adres pojawi się po uruchomieniu", "The address will appear after starting"), color = Muted, fontSize = 12.sp)
                Text(session.url.ifBlank { "http://—:4747" }, fontFamily = FontFamily.Monospace, color = Lime, fontSize = 16.sp)
                if (session.running) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { copy("${session.url}/#${session.token}") }) { Text(t("Kopiuj link · auto start", "Copy link · auto connect")) }
                        OutlinedButton(onClick = { copy("${session.url}/video.mjpg?token=${session.token}") }) { Text(t("Link VLC", "VLC link")) }
                    }
                    TextButton(onClick = toggle, contentPadding = PaddingValues(0.dp)) {
                        Text(if (reveal) t("Ukryj klucz sesji", "Hide session key") else t("Pokaż klucz sesji", "Show session key"), fontSize = 12.sp)
                    }
                    if (reveal) Text(session.token, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                HorizontalDivider(color = Line)
                Text("PRZEGLĄDARKA  /  OBS  /  VLC", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun SettingsPanel(config: StreamConfig, busy: Boolean, hasTorch: Boolean, pro: Boolean) {
    var quality by remember(config.quality) { mutableFloatStateOf(config.quality.toFloat()) }
    var port by remember(config.port) { mutableStateOf(config.port.toString()) }
    fun change(transform: (StreamConfig) -> StreamConfig) { StreamState.config.update { transform(it).validated() } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(t("03 / USTAWIENIA STUDIA", "03 / STUDIO SETTINGS"))
        Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !config.front, onClick = { change { it.copy(front = false) } }, label = { Text(t("Tył", "Rear")) })
                    FilterChip(selected = config.front, onClick = { change { it.copy(front = true, torch = false) } }, label = { Text(t("Przód", "Front")) })
                    FilterChip(selected = config.torch, enabled = !config.front && (!busy || hasTorch),
                        onClick = { change { it.copy(torch = !it.torch) } }, label = { Text(t("Lampa", "Torch")) })
                }
                Text(t("Rozdzielczość docelowa", "Target resolution"), color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StreamConfig.resolutions.forEach { (w, h) ->
                        FilterChip(selected = config.width == w, enabled = pro || h <= 480, onClick = { change { it.copy(width = w, height = h) } },
                            label = { Text(if (h > 480 && !pro) "${h}p PRO" else "${h}p") })
                    }
                }
                Text(t("Limit klatek na sekundę", "Frame-rate limit"), color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StreamConfig.frameRates.forEach { fps ->
                        FilterChip(selected = config.fps == fps, onClick = { change { it.copy(fps = fps) } }, label = { Text("$fps FPS") })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(t("Jakość JPEG", "JPEG quality"), color = Muted, fontSize = 12.sp)
                    Text("${quality.toInt()}% · ", color = Lime, fontSize = 12.sp)
                }
                Slider(value = quality, onValueChange = { quality = it }, valueRange = 40f..90f,
                    onValueChangeFinished = { change { it.copy(quality = quality.toInt()) } })
                HorizontalDivider(color = Line)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t("Mikrofon", "Microphone"), fontSize = 14.sp)
                        Text("PCM · 48 kHz · mono", color = Muted, fontSize = 11.sp)
                    }
                    Switch(checked = config.audio, enabled = !busy, onCheckedChange = { value -> change { it.copy(audio = value) } })
                }
                OutlinedTextField(value = port, onValueChange = {
                    port = it.filter(Char::isDigit).take(5)
                    port.toIntOrNull()?.takeIf { n -> n in 1024..65535 }?.let { n -> change { c -> c.copy(port = n) } }
                }, label = { Text(t("Port HTTP", "HTTP port")) }, enabled = !busy, singleLine = true,
                    isError = port.toIntOrNull()?.let { it !in 1024..65535 } ?: true, modifier = Modifier.fillMaxWidth())
                Text(t("Mikrofon i port zmienisz przed startem. Dostępny rozmiar i FPS zależą od telefonu.", "Change microphone and port before starting. Available resolution and FPS depend on your phone."), color = Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) { Text(text, color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp) }

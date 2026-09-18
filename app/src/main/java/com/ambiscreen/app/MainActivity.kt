package com.ambiscreen.app

import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ambiscreen.app.audio.AudioReactiveService
import com.ambiscreen.app.capture.Edge
import com.ambiscreen.app.capture.LedLayoutConfig
import com.ambiscreen.app.capture.ScreenCaptureService
import com.ambiscreen.app.discovery.DiscoveredWled
import com.ambiscreen.app.discovery.WledDiscovery
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class CaptureMode { SCREEN, AUDIO }

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private var pendingMode: CaptureMode = CaptureMode.SCREEN

    private val captureConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode != RESULT_OK || data == null) return@registerForActivityResult

        val intent = when (pendingMode) {
            CaptureMode.SCREEN -> Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            }
            CaptureMode.AUDIO -> Intent(this, AudioReactiveService::class.java).apply {
                action = AudioReactiveService.ACTION_START
                putExtra(AudioReactiveService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(AudioReactiveService.EXTRA_RESULT_DATA, data)
            }
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* sonuçtan bağımsız devam edilir; reddedilirse yalnızca bildirim görünmez */ }

    private val recordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            pendingMode = CaptureMode.AUDIO
            launchCaptureConsent()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(applicationContext)

        setContent {
            MaterialTheme {
                Surface {
                    AmbiScreenApp(
                        settingsRepository = settingsRepository,
                        onStart = ::requestCaptureAndStart,
                        onStop = ::stopCapture,
                    )
                }
            }
        }
    }

    private fun requestCaptureAndStart(mode: CaptureMode) {
        ensureNotificationPermission()
        when (mode) {
            CaptureMode.SCREEN -> {
                pendingMode = CaptureMode.SCREEN
                launchCaptureConsent()
            }
            CaptureMode.AUDIO -> {
                if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                } else {
                    pendingMode = CaptureMode.AUDIO
                    launchCaptureConsent()
                }
            }
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun launchCaptureConsent() {
        val projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureConsentLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun stopCapture(mode: CaptureMode) {
        val serviceClass = when (mode) {
            CaptureMode.SCREEN -> ScreenCaptureService::class.java
            CaptureMode.AUDIO -> AudioReactiveService::class.java
        }
        val action = when (mode) {
            CaptureMode.SCREEN -> ScreenCaptureService.ACTION_STOP
            CaptureMode.AUDIO -> AudioReactiveService.ACTION_STOP
        }
        startService(Intent(this, serviceClass).apply { this.action = action })
    }
}

@Composable
fun AmbiScreenApp(
    settingsRepository: SettingsRepository,
    onStart: (CaptureMode) -> Unit,
    onStop: (CaptureMode) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by settingsRepository.settingsFlow.collectAsState(initial = AmbiSettings())
    var runningMode by remember { mutableStateOf<CaptureMode?>(null) }
    val audioModeAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    val discovery = remember { WledDiscovery(context) }
    val discoveredDevices = remember { mutableStateListOf<DiscoveredWled>() }
    var isScanning by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { discovery.stop() }
    }

    fun startScan() {
        discoveredDevices.clear()
        isScanning = true
        discovery.start { found ->
            if (discoveredDevices.none { it.host == found.host }) {
                discoveredDevices.add(found)
            }
        }
        scope.launch {
            delay(6000)
            discovery.stop()
            isScanning = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "AmbiScreen",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Ekran renklerini perde arkasındaki WLED LED şeridine yansıtır.",
            style = MaterialTheme.typography.bodyMedium,
        )

        TextField(
            value = settings.wledIp,
            onValueChange = { newIp ->
                scope.launch { settingsRepository.update(settings.copy(wledIp = newIp)) }
            },
            label = { Text("WLED IP adresi") },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedButton(
            onClick = { startScan() },
            enabled = !isScanning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isScanning) "Ağda WLED aranıyor…" else "Ağda WLED cihazı ara")
        }

        if (discoveredDevices.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Bulunanlar (dokunarak seçin):", style = MaterialTheme.typography.labelMedium)
                discoveredDevices.forEach { device ->
                    Text(
                        text = "${device.name} — ${device.host}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    settingsRepository.update(settings.copy(wledIp = device.host))
                                }
                            }
                            .padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        TextField(
            value = settings.wledPort.toString(),
            onValueChange = { value ->
                val port = value.toIntOrNull() ?: return@TextField
                scope.launch { settingsRepository.update(settings.copy(wledPort = port)) }
            },
            label = { Text("WLED DDP portu (varsayılan 4048)") },
            modifier = Modifier.fillMaxWidth(),
        )

        LedLayoutEditor(
            layout = settings.ledLayout,
            onChange = { newLayout ->
                scope.launch { settingsRepository.update(settings.copy(ledLayoutRaw = newLayout.serialize())) }
            },
        )

        LabeledSlider(
            label = "Kenar örnekleme payı: %${settings.marginPercent}",
            value = settings.marginPercent.toFloat(),
            range = 2f..30f,
            onValueChange = { v ->
                scope.launch { settingsRepository.update(settings.copy(marginPercent = v.toInt())) }
            },
        )

        LabeledSlider(
            label = "Parlaklık: %${settings.brightnessPercent}",
            value = settings.brightnessPercent.toFloat(),
            range = 0f..100f,
            onValueChange = { v ->
                scope.launch { settingsRepository.update(settings.copy(brightnessPercent = v.toInt())) }
            },
        )

        LabeledSlider(
            label = "Yumuşatma: %${settings.smoothingPercent}",
            value = settings.smoothingPercent.toFloat(),
            range = 0f..90f,
            onValueChange = { v ->
                scope.launch { settingsRepository.update(settings.copy(smoothingPercent = v.toInt())) }
            },
        )

        LabeledSlider(
            label = "Ses hassasiyeti (yalnızca ses-tepkili modda): %${settings.audioSensitivityPercent}",
            value = settings.audioSensitivityPercent.toFloat(),
            range = 10f..200f,
            onValueChange = { v ->
                scope.launch { settingsRepository.update(settings.copy(audioSensitivityPercent = v.toInt())) }
            },
        )

        LabeledSlider(
            label = "Güncelleme aralığı: ${settings.intervalMs} ms",
            value = settings.intervalMs.toFloat(),
            range = 40f..500f,
            onValueChange = { v ->
                scope.launch { settingsRepository.update(settings.copy(intervalMs = v.toLong())) }
            },
        )

        Column {
            Text("LED sırasını ters çevir (fiziksel bağlantı yönüne göre)")
            Switch(
                checked = settings.reverseDirection,
                onCheckedChange = { checked ->
                    scope.launch { settingsRepository.update(settings.copy(reverseDirection = checked)) }
                },
            )
        }

        if (runningMode != null) {
            Button(
                onClick = {
                    onStop(runningMode!!)
                    runningMode = null
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (runningMode == CaptureMode.AUDIO) {
                        "Durdur (ses-tepkili mod)"
                    } else {
                        "Durdur (ekran modu)"
                    },
                )
            }
        } else {
            Button(
                onClick = {
                    onStart(CaptureMode.SCREEN)
                    runningMode = CaptureMode.SCREEN
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Ekran senkronizasyonunu başlat")
            }

            Column {
                Button(
                    onClick = {
                        onStart(CaptureMode.AUDIO)
                        runningMode = CaptureMode.AUDIO
                    },
                    enabled = audioModeAvailable,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Ses-tepkili modu başlat (Netflix gibi DRM'li akışlar için)")
                }
                if (!audioModeAvailable) {
                    Text(
                        "Bu mod Android 10 ve üzeri gerektirir.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        "Ekranı okumaz; çalan sesin bas/tiz enerjisine göre renk üretir. " +
                            "Kaynak uygulama sesi de yakalamaya kapatmışsa bu mod da tepki vermez.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, valueRange = range, onValueChange = onValueChange)
    }
}

private val EDGE_LABELS = listOf(
    Edge.TOP to "Üst",
    Edge.RIGHT to "Sağ",
    Edge.BOTTOM to "Alt",
    Edge.LEFT to "Sol",
)

/**
 * Her kenarı ayrı açıp/kapatıp kendi LED sayısını girmeye izin verir; bu
 * sayede tam perimetre, yalnızca üst-alt veya L-şekilli (örn. üst+sol) gibi
 * farklı fiziksel LED kurulumları tek arayüzden tanımlanabilir.
 */
@Composable
private fun LedLayoutEditor(
    layout: LedLayoutConfig,
    onChange: (LedLayoutConfig) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("LED yerleşimi (kenar başına)", style = MaterialTheme.typography.titleSmall)

        EDGE_LABELS.forEach { (edge, label) ->
            val config = layout.configFor(edge)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { checked -> onChange(layout.with(edge, config.copy(enabled = checked))) },
                )
                Text(label, modifier = Modifier.width(40.dp))
                OutlinedTextField(
                    value = config.ledCount.toString(),
                    onValueChange = { value ->
                        val count = value.toIntOrNull() ?: return@OutlinedTextField
                        onChange(layout.with(edge, config.copy(ledCount = count)))
                    },
                    enabled = config.enabled,
                    label = { Text("LED") },
                    modifier = Modifier.width(110.dp),
                )
            }
        }

        Text("Başlangıç kenarı (şeridin ilk LED'i)", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EDGE_LABELS.forEach { (edge, label) ->
                val selected = layout.startEdge == edge
                Text(
                    text = if (selected) "● $label" else "○ $label",
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable { onChange(layout.copy(startEdge = edge)) },
                )
            }
        }

        Text(
            "Toplam LED: ${layout.totalLedCount()}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

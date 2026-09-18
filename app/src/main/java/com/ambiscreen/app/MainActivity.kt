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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ambiscreen.app.capture.ScreenCaptureService
import com.ambiscreen.app.discovery.DiscoveredWled
import com.ambiscreen.app.discovery.WledDiscovery
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(this, intent)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* sonuçtan bağımsız devam edilir; reddedilirse yalnızca bildirim görünmez */ }

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

    private fun requestCaptureAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun stopCapture() {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP
        }
        startService(intent)
    }
}

@Composable
fun AmbiScreenApp(
    settingsRepository: SettingsRepository,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by settingsRepository.settingsFlow.collectAsState(initial = AmbiSettings())
    var isRunning by remember { mutableStateOf(false) }

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

        TextField(
            value = settings.ledCount.toString(),
            onValueChange = { value ->
                val count = value.toIntOrNull() ?: return@TextField
                scope.launch { settingsRepository.update(settings.copy(ledCount = count)) }
            },
            label = { Text("LED sayısı (şeritteki toplam)") },
            modifier = Modifier.fillMaxWidth(),
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

        Button(
            onClick = {
                if (isRunning) {
                    onStop()
                } else {
                    onStart()
                }
                isRunning = !isRunning
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isRunning) "Durdur" else "Ekran senkronizasyonunu başlat")
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

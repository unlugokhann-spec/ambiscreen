package com.ambiscreen.app

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ambiscreen.app.capture.LedLayoutConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ambiscreen_settings")

/**
 * WLED, DDP protokolünü (gerçek zamanlı UDP renk akışı) portu 4048 üzerinden dinler.
 * marginPercent: perimetrenin kenarından, renk örneklemesi için içeri doğru alınan pay.
 * smoothingPercent: 0 = anlık, yüksek değer = daha yumuşak ama daha gecikmeli geçiş.
 */
data class AmbiSettings(
    val wledIp: String = "",
    val wledPort: Int = 4048,
    val ledLayoutRaw: String = LedLayoutConfig.DEFAULT.serialize(),
    val marginPercent: Int = 12,
    val intervalMs: Long = 120,
    val reverseDirection: Boolean = false,
    val brightnessPercent: Int = 100,
    val smoothingPercent: Int = 35,
) {
    val ledLayout: LedLayoutConfig get() = LedLayoutConfig.deserialize(ledLayoutRaw)
}

object SettingsKeys {
    val WLED_IP = stringPreferencesKey("wled_ip")
    val WLED_PORT = intPreferencesKey("wled_port")
    val LED_LAYOUT = stringPreferencesKey("led_layout")
    val MARGIN_PERCENT = intPreferencesKey("margin_percent")
    val INTERVAL_MS = longPreferencesKey("interval_ms")
    val REVERSE_DIRECTION = booleanPreferencesKey("reverse_direction")
    val BRIGHTNESS_PERCENT = intPreferencesKey("brightness_percent")
    val SMOOTHING_PERCENT = intPreferencesKey("smoothing_percent")
}

class SettingsRepository(private val context: Context) {

    val settingsFlow: Flow<AmbiSettings> = context.dataStore.data.map { prefs ->
        val defaults = AmbiSettings()
        AmbiSettings(
            wledIp = prefs[SettingsKeys.WLED_IP] ?: defaults.wledIp,
            wledPort = prefs[SettingsKeys.WLED_PORT] ?: defaults.wledPort,
            ledLayoutRaw = prefs[SettingsKeys.LED_LAYOUT] ?: defaults.ledLayoutRaw,
            marginPercent = prefs[SettingsKeys.MARGIN_PERCENT] ?: defaults.marginPercent,
            intervalMs = prefs[SettingsKeys.INTERVAL_MS] ?: defaults.intervalMs,
            reverseDirection = prefs[SettingsKeys.REVERSE_DIRECTION] ?: defaults.reverseDirection,
            brightnessPercent = prefs[SettingsKeys.BRIGHTNESS_PERCENT] ?: defaults.brightnessPercent,
            smoothingPercent = prefs[SettingsKeys.SMOOTHING_PERCENT] ?: defaults.smoothingPercent,
        )
    }

    suspend fun update(settings: AmbiSettings) {
        context.dataStore.edit { prefs ->
            prefs[SettingsKeys.WLED_IP] = settings.wledIp
            prefs[SettingsKeys.WLED_PORT] = settings.wledPort
            prefs[SettingsKeys.LED_LAYOUT] = settings.ledLayoutRaw
            prefs[SettingsKeys.MARGIN_PERCENT] = settings.marginPercent
            prefs[SettingsKeys.INTERVAL_MS] = settings.intervalMs
            prefs[SettingsKeys.REVERSE_DIRECTION] = settings.reverseDirection
            prefs[SettingsKeys.BRIGHTNESS_PERCENT] = settings.brightnessPercent
            prefs[SettingsKeys.SMOOTHING_PERCENT] = settings.smoothingPercent
        }
    }
}

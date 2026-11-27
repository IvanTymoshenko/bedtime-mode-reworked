package app.bedtimemode

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.bedtimemode.ui.BedtimeTheme
import app.bedtimemode.ui.FancyScreen
import app.bedtimemode.ui.FancyTheme
import app.bedtimemode.ui.MainScreen
import app.bedtimemode.ui.Onboarding

class MainActivity : ComponentActivity() {
    /** Set when opened from the "update available" notification. */
    private var showUpdate by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showUpdate = intent?.getBooleanExtra(Updater.EXTRA_SHOW_UPDATE, false) == true
        val prefs = Prefs(this)
        setContent {
            var onboarded by remember { mutableStateOf(prefs.onboardingDone) }
            var fancy by remember { mutableStateOf(prefs.fancyUi) }
            var accent by remember { mutableStateOf(prefs.fancyAccent) }
            DisposableEffect(Unit) {
                val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> fancy = prefs.fancyUi; accent = prefs.fancyAccent }
                prefs.sp.registerOnSharedPreferenceChangeListener(l)
                onDispose { prefs.sp.unregisterOnSharedPreferenceChangeListener(l) }
            }
            val useFancy = onboarded && fancy
            LaunchedEffect(useFancy) {
                // Fancy is always dark: force light status/nav bar icons; classic follows the system.
                if (useFancy) enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
                else enableEdgeToEdge()
            }
            if (!onboarded) {
                BedtimeTheme { Onboarding(onDone = { onboarded = true }) }
            } else if (fancy) {
                FancyTheme(accent) { FancyScreen(showUpdate = showUpdate, onUpdateShown = { showUpdate = false }) }
            } else {
                BedtimeTheme { MainScreen(showUpdate = showUpdate, onUpdateShown = { showUpdate = false }) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(Updater.EXTRA_SHOW_UPDATE, false)) showUpdate = true
    }
}

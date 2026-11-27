package app.bedtimemode

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.bedtimemode.ui.BedtimeTheme
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
            BedtimeTheme {
                var onboarded by androidx.compose.runtime.remember { mutableStateOf(prefs.onboardingDone) }
                if (!onboarded) {
                    Onboarding(onDone = { onboarded = true })
                } else {
                    MainScreen(showUpdate = showUpdate, onUpdateShown = { showUpdate = false })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(Updater.EXTRA_SHOW_UPDATE, false)) showUpdate = true
    }
}

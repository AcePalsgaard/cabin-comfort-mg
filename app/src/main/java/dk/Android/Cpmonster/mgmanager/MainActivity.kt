package dk.Android.Cpmonster.mgmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dk.Android.Cpmonster.mgmanager.data.repository.SettingsRepositoryImpl
import dk.Android.Cpmonster.mgmanager.data.repository.dataStore
import dk.Android.Cpmonster.mgmanager.ui.navigation.AppNavigation
import dk.Android.Cpmonster.mgmanager.ui.settings.SettingsViewModel
import dk.Android.Cpmonster.mgmanager.ui.theme.MGManagerTheme

class MainActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory(SettingsRepositoryImpl(applicationContext.dataStore))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            MGManagerTheme(themeMode = themeMode) {
                AppNavigation(viewModel = settingsViewModel)
            }
        }
    }
}

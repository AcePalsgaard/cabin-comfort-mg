package dk.Android.Cpmonster.mgmanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dk.Android.Cpmonster.mgmanager.data.model.AuthSession
import dk.Android.Cpmonster.mgmanager.data.model.ThemeMode
import dk.Android.Cpmonster.mgmanager.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = repository.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemeMode.SYSTEM_DEFAULT
        )

    val authSession: StateFlow<AuthSession> = repository.authSession
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AuthSession()
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun login(username: String, token: String, userId: String) {
        viewModelScope.launch {
            repository.setAuthSession(
                AuthSession(
                    isLoggedIn = true,
                    token = token,
                    username = username,
                    userId = userId
                )
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.clearAuthSession()
        }
    }

    class Factory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

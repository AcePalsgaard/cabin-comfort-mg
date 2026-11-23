package dk.Android.Cpmonster.mgmanager.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dk.Android.Cpmonster.mgmanager.data.model.AuthSession
import dk.Android.Cpmonster.mgmanager.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

interface SettingsRepository {
    val themeMode: Flow<ThemeMode>
    val authSession: Flow<AuthSession>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setAuthSession(session: AuthSession)
    suspend fun clearAuthSession()
}

class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
        val USERNAME = stringPreferencesKey("username")
        val USER_ID = stringPreferencesKey("user_id")
    }

    override val themeMode: Flow<ThemeMode> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val modeString = preferences[PreferencesKeys.THEME_MODE]
            ThemeMode.fromString(modeString)
        }

    override val authSession: Flow<AuthSession> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AuthSession(
                isLoggedIn = preferences[PreferencesKeys.IS_LOGGED_IN] ?: false,
                token = preferences[PreferencesKeys.AUTH_TOKEN],
                username = preferences[PreferencesKeys.USERNAME],
                userId = preferences[PreferencesKeys.USER_ID]
            )
        }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    override suspend fun setAuthSession(session: AuthSession) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_LOGGED_IN] = session.isLoggedIn
            if (session.token != null) preferences[PreferencesKeys.AUTH_TOKEN] = session.token else preferences.remove(PreferencesKeys.AUTH_TOKEN)
            if (session.username != null) preferences[PreferencesKeys.USERNAME] = session.username else preferences.remove(PreferencesKeys.USERNAME)
            if (session.userId != null) preferences[PreferencesKeys.USER_ID] = session.userId else preferences.remove(PreferencesKeys.USER_ID)
        }
    }

    override suspend fun clearAuthSession() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_LOGGED_IN] = false
            preferences.remove(PreferencesKeys.AUTH_TOKEN)
            preferences.remove(PreferencesKeys.USERNAME)
            preferences.remove(PreferencesKeys.USER_ID)
        }
    }
}

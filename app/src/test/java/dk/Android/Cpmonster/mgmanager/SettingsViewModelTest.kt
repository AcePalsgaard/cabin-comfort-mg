package dk.Android.Cpmonster.mgmanager

import dk.Android.Cpmonster.mgmanager.data.model.AuthSession
import dk.Android.Cpmonster.mgmanager.data.model.ThemeMode
import dk.Android.Cpmonster.mgmanager.data.repository.SettingsRepository
import dk.Android.Cpmonster.mgmanager.ui.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeSettingsRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeSettingsRepository()
        viewModel = SettingsViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setThemeMode updates theme preference`() = runTest {
        backgroundScope.launch { viewModel.themeMode.collect {} }
        viewModel.setThemeMode(ThemeMode.DARK)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
    }

    @Test
    fun `login updates auth session state`() = runTest {
        backgroundScope.launch { viewModel.authSession.collect {} }
        viewModel.login("testuser", "token_123", "user_456")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.authSession.value.isLoggedIn)
        assertEquals("testuser", viewModel.authSession.value.username)
        assertEquals("token_123", viewModel.authSession.value.token)
        assertEquals("user_456", viewModel.authSession.value.userId)
    }

    @Test
    fun `logout clears auth session state`() = runTest {
        backgroundScope.launch { viewModel.authSession.collect {} }
        viewModel.login("testuser", "token_123", "user_456")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.logout()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.authSession.value.isLoggedIn)
    }

}

class FakeSettingsRepository : SettingsRepository {
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM_DEFAULT)
    override val themeMode: Flow<ThemeMode> = _themeMode

    private val _authSession = MutableStateFlow(AuthSession())
    override val authSession: Flow<AuthSession> = _authSession

    override suspend fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    override suspend fun setAuthSession(session: AuthSession) {
        _authSession.value = session
    }

    override suspend fun clearAuthSession() {
        _authSession.value = AuthSession()
    }

}

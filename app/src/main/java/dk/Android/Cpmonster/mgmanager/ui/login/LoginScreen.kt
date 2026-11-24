package dk.Android.Cpmonster.mgmanager.ui.login

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dk.Android.Cpmonster.mgmanager.AppContainer
import dk.Android.Cpmonster.mgmanager.saic.SaicLoginRejectedException
import dk.Android.Cpmonster.mgmanager.saic.SaicRegion
import android.util.Log
import dk.Android.Cpmonster.mgmanager.saic.SaicNetworkException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUi(
    val username: String = "",
    val isEmail: Boolean = true,
    val countryCode: String = "45",
    val busy: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(private val c: AppContainer) : ViewModel() {
    val password = TextFieldState()
    private val _ui = MutableStateFlow(LoginUi())
    val ui: StateFlow<LoginUi> = _ui

    /** Server to log in on. Can be changed here (picker dialog) or under Settings. */
    val server: StateFlow<SaicRegion> = c.auth.state.map { it.region }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SaicRegion.EU)

    init {
        viewModelScope.launch {
            val s = c.auth.state.first()
            _ui.update {
                it.copy(
                    username = s.username.orEmpty(),
                    isEmail = s.isEmail,
                    countryCode = s.countryCode ?: it.countryCode,
                    error = when {
                        // Logged in while MG was down; now MG is back and said no.
                        s.needsLogin && s.unverified -> "MG is back online but didn't accept that login. Please check your username and password."
                        s.needsLogin && s.rejectReason != null ->
                            "MG stopped accepting the saved login. MG said: ${s.rejectReason}\n" +
                                "Please log in again. If it keeps happening, wait a few minutes and check your password in iSMART."
                        s.needsLogin -> "Please log in again"
                        else -> null
                    },
                )
            }
        }
    }

    /** Pick a server before logging in. Not logged in yet, so nothing to log out. */
    fun selectServer(region: SaicRegion) {
        viewModelScope.launch { c.changeServer(region) }
    }

    fun update(f: (LoginUi) -> LoginUi) = _ui.update { f(it).copy(error = null) }

    fun login() {
        val s = _ui.value
        val pw = password.text.toString()
        if (s.username.isBlank() || pw.isEmpty()) {
            _ui.update { it.copy(error = "Enter your iSMART login") }
            return
        }
        _ui.update { it.copy(busy = true, error = null) }
        // Runs in the app scope: logging in swaps the screen, and that must not cancel the login.
        c.scope.launch {
            val error: String? = try {
                // Returns false when MG's servers don't answer: we let the user in anyway and
                // check the password in the background once they're back.
                if (!c.auth.login(s.username, pw, s.isEmail, s.countryCode.takeUnless { s.isEmail })) {
                    Log.w(TAG, "Login: MG servers not responding, continuing unverified")
                }
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: SaicLoginRejectedException) {
                e.message ?: "Wrong username or password"
            } catch (e: SaicNetworkException) {
                Log.w(TAG, "Login: network", e)
                if (e.phoneOffline) "No internet connection. Check your connection and try again."
                else "Can't reach MG right now. Try again in a moment." + debugDetail(e)
            } catch (e: Exception) {
                Log.e(TAG, "Login failed", e)
                "Login failed." + debugDetail(e)
            }
            if (error == null) {
                // Logged in. Fetching car data is the queue's job; it must never turn into a login error.
                runCatching { c.onLoggedIn() }.onFailure { Log.w(TAG, "Post-login refresh", it) }
            }
            withContext(Dispatchers.Main) {
                if (error == null) password.clearText()
                _ui.update { it.copy(busy = false, error = error) }
            }
        }
    }

    /** Guest mode: look around with a pretend car. Nothing is sent to MG. */
    fun tryDemo() {
        if (_ui.value.busy) return
        c.scope.launch { c.startDemo() }
    }

    private fun debugDetail(e: Throwable): String =
        if (c.isDebuggable) "\n(${e::class.simpleName}: ${e.message})" else ""

    companion object {
        private const val TAG = "Login"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: LoginViewModel, modifier: Modifier = Modifier) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val server by viewModel.server.collectAsStateWithLifecycle()
    var showPassword by remember { mutableStateOf(false) }
    var showServerPicker by remember { mutableStateOf(false) }

    if (showServerPicker) {
        AlertDialog(
            onDismissRequest = { showServerPicker = false },
            title = { Text("Server") },
            text = {
                Column(Modifier.selectableGroup()) {
                    SaicRegion.selectable.forEach { r ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = r == server,
                                    role = Role.RadioButton,
                                    onClick = {
                                        // Select and close in one tap.
                                        viewModel.selectServer(r)
                                        showServerPicker = false
                                    },
                                )
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = r == server, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(r.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showServerPicker = false }) { Text("Cancel") } },
        )
    }

    Box(
        modifier
            .fillMaxSize()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Rounded.DirectionsCar,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("Cabin Comfort for MG", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Log in with your MG iSMART account",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))

            // Server first: logging in on the wrong one fails, so it must be hard to miss.
            OutlinedButton(
                onClick = { showServerPicker = true },
                enabled = !ui.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Icon(Icons.Rounded.Public, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Server",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(server.label, style = MaterialTheme.typography.bodyLarge)
                }
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Change server")
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = ui.isEmail,
                    onClick = { viewModel.update { it.copy(isEmail = true) } },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Email") }
                SegmentedButton(
                    selected = !ui.isEmail,
                    onClick = { viewModel.update { it.copy(isEmail = false) } },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Phone") }
            }

            if (!ui.isEmail) {
                OutlinedTextField(
                    value = ui.countryCode,
                    onValueChange = { v -> viewModel.update { it.copy(countryCode = v.filter(Char::isDigit)) } },
                    label = { Text("Country code") },
                    prefix = { Text("+") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = ui.username,
                onValueChange = { v -> viewModel.update { it.copy(username = v) } },
                label = { Text(if (ui.isEmail) "Email" else "Phone number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (ui.isEmail) KeyboardType.Email else KeyboardType.Phone,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedSecureTextField(
                state = viewModel.password,
                label = { Text("Password") },
                textObfuscationMode = if (showPassword) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                        )
                    }
                },
                onKeyboardAction = { viewModel.login() },
                modifier = Modifier.fillMaxWidth(),
            )

            ui.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = viewModel::login,
                enabled = !ui.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (ui.busy) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Log in")
                }
            }

            OutlinedButton(
                onClick = viewModel::tryDemo,
                enabled = !ui.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("Try it as a guest")
            }
            Text(
                "No account needed. Uses a pretend car – nothing is sent anywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

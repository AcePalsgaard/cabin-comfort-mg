package dk.Android.Cpmonster.mgmanager.data.model

data class AuthSession(
    val isLoggedIn: Boolean = false,
    val token: String? = null,
    val username: String? = null,
    val userId: String? = null
)

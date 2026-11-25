package dk.Android.Cpmonster.mgmanager.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface Screen : NavKey {
    @Serializable
    data object Login : Screen

    @Serializable
    data object Main : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data object About : Screen

    @Serializable
    data object Schedule : Screen

    @Serializable
    data class ProfileEdit(val id: Long) : Screen
}

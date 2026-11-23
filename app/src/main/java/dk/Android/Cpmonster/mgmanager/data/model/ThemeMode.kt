package dk.Android.Cpmonster.mgmanager.data.model

enum class ThemeMode {
    SYSTEM_DEFAULT,
    LIGHT,
    DARK;

    companion object {
        fun fromString(value: String?): ThemeMode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: SYSTEM_DEFAULT
        }
    }
}

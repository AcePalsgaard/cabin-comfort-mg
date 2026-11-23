package dk.Android.Cpmonster.mgmanager

/**
 * DEV-ONLY: single switch for developer features (test/mock server etc.).
 *
 * Follows BuildConfig.DEBUG, so it is false in every release build and
 * `if (DevOnly.ENABLED)` blocks are never shown to users.
 * Search the project for "DEV-ONLY" to find every developer-only spot.
 */
object DevOnly {
    val ENABLED: Boolean = BuildConfig.DEBUG
}

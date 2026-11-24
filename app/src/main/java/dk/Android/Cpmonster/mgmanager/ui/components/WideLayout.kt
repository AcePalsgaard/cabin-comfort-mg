package dk.Android.Cpmonster.mgmanager.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * Two-column layout for landscape / wide screens.
 *
 * Decided from the whole window, not the space left after system bars and the camera cutout:
 * those sit on different sides depending on which way the phone is turned, so measuring what's
 * left gave one layout turned left and another turned right (seen on a OnePlus 11).
 */
@Composable
fun isWideLayout(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    return isWideLayout(size.width / density, size.height / density)
}

fun isWideLayout(widthDp: Float, heightDp: Float): Boolean = widthDp >= 600f && widthDp > heightDp

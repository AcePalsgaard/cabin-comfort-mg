package dk.Android.Cpmonster.mgmanager.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dk.Android.Cpmonster.mgmanager.saic.ServerHealth
import dk.Android.Cpmonster.mgmanager.saic.ServerSnapshot
import dk.Android.Cpmonster.mgmanager.saic.ServerStatus
import kotlinx.coroutines.delay

private val Good = Color(0xFF2EB872)
private val Slow = Color(0xFFF2B705)
private val Down = Color(0xFFE5484D)
/** The phone can't get out: calm slate grey, not alarm red – it's not MG's fault. */
private val Offline = Color(0xFF8E9AAF)

/** What the front page shows about MG right now. */
data class ShownServerState(
    val health: ServerHealth,
    /** A call or a refresh is in progress (the ring spins). */
    val waiting: Boolean,
    /** Short line under "Updating…" when something is wrong, else null. */
    val message: String?,
)

/**
 * Turns the raw server state into what to show, re-evaluated every 250 ms while something
 * is in progress, so it turns yellow/red while waiting rather than only after a timeout.
 * [server] is null in demo mode (nothing is sent, so there is nothing to show).
 */
@Composable
fun rememberShownServerState(server: ServerSnapshot?, busy: Boolean): ShownServerState {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // When the current refresh started; reset when it finishes.
    var refreshSince by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(busy) { refreshSince = if (busy) System.currentTimeMillis() else null }
    val ticking = busy || server?.waitingSince != null
    LaunchedEffect(ticking) {
        now = System.currentTimeMillis()
        while (ticking) {
            delay(250)
            now = System.currentTimeMillis()
        }
    }
    if (server == null) return ShownServerState(ServerHealth.UNKNOWN, busy, message = null)
    val since = refreshSince.takeIf { busy }
    val health = ServerStatus.displayHealth(server, now, since)
    return ShownServerState(
        health = health,
        waiting = ticking,
        message = when {
            health == ServerHealth.OFFLINE -> "No connection"
            health == ServerHealth.DOWN || ServerStatus.isStuck(now, since) -> "MG not responding"
            else -> null
        },
    )
}

/** Yellow/red used for both the ring and the "MG not responding" text. */
fun ServerHealth.statusColor(neutral: Color): Color = when (this) {
    ServerHealth.OK -> Good
    ServerHealth.SLOW -> Slow
    ServerHealth.DOWN -> Down
    ServerHealth.OFFLINE -> Offline
    ServerHealth.UNKNOWN -> neutral
}

/**
 * Refresh button wrapped in a ring that shows how MG's servers are doing:
 * green = answering fast, yellow = slow, red = not answering, grey = don't know yet.
 * The ring spins while we're waiting for an answer. Tap to refresh.
 */
@Composable
fun ServerStatusRing(
    state: ShownServerState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val health = state.health
    val waiting = state.waiting

    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val target = health.statusColor(neutral)
    val color by animateColorAsState(target, label = "ring-color")
    val arcAlpha by animateFloatAsState(if (waiting) 1f else 0f, label = "ring-arc")

    val t = rememberInfiniteTransition(label = "ring")
    // Slower spin when things are slow or down: it "feels" like waiting.
    val period = when (health) {
        ServerHealth.SLOW -> 1_600
        ServerHealth.DOWN, ServerHealth.OFFLINE -> 2_200
        else -> 1_000
    }
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(period, easing = LinearEasing)), label = "spin")
    val pulse by t.animateFloat(0.35f, 0f, infiniteRepeatable(tween(1_400), RepeatMode.Restart), label = "pulse")
    val pulseGrow by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1_400), RepeatMode.Restart), label = "pulse-grow")

    val label = when (health) {
        ServerHealth.OK -> "MG server answering"
        ServerHealth.SLOW -> "MG server slow"
        ServerHealth.DOWN -> "MG server not responding"
        ServerHealth.OFFLINE -> "No internet connection"
        ServerHealth.UNKNOWN -> "MG server status unknown"
    }

    IconButton(
        onClick = onRefresh,
        modifier = modifier.size(48.dp).semantics { contentDescription = "Refresh. $label" },
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2 + 2.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)

                // Soft ripple when MG is down, so it catches the eye without any text.
                if (health == ServerHealth.DOWN) {
                    drawCircle(
                        color = color.copy(alpha = pulse),
                        radius = arcSize.width / 2 + pulseGrow * 4.dp.toPx(),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                // Steady ring: faint while spinning, full when idle.
                drawArc(
                    color = color.copy(alpha = 0.25f + 0.65f * (1f - arcAlpha)),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(stroke),
                )
                // Spinning comet tail while waiting for an answer.
                if (arcAlpha > 0f) {
                    rotate(angle) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                0f to Color.Transparent,
                                0.30f to color.copy(alpha = 0.2f * arcAlpha),
                                0.75f to color.copy(alpha = arcAlpha),
                                // Arc ends at 300° (0.83); the round cap pokes a bit past it.
                                // Fade out after that so the tail's cap (wrapping past 0°) stays invisible.
                                0.88f to color.copy(alpha = arcAlpha),
                                0.95f to Color.Transparent,
                                1f to Color.Transparent,
                                center = center,
                            ),
                            startAngle = 0f, sweepAngle = 300f, useCenter = false,
                            topLeft = topLeft, size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                }
            }
            Icon(
                when (health) {
                    ServerHealth.DOWN -> Icons.Rounded.CloudOff
                    ServerHealth.OFFLINE -> Icons.Rounded.WifiOff
                    else -> Icons.Rounded.Refresh
                },
                contentDescription = null,
                tint = if (health == ServerHealth.DOWN || health == ServerHealth.OFFLINE) color else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

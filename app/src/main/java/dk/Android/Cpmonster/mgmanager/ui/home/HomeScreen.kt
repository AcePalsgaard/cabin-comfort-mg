package dk.Android.Cpmonster.mgmanager.ui.home

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dk.Android.Cpmonster.mgmanager.AppContainer
import dk.Android.Cpmonster.mgmanager.data.db.Profile
import dk.Android.Cpmonster.mgmanager.data.db.ProfileKind
import dk.Android.Cpmonster.mgmanager.data.db.VehicleState
import dk.Android.Cpmonster.mgmanager.data.profile.ActiveProfile
import dk.Android.Cpmonster.mgmanager.data.profile.ProfileSuggester
import dk.Android.Cpmonster.mgmanager.queue.JobType
import dk.Android.Cpmonster.mgmanager.saic.ServerSnapshot
import dk.Android.Cpmonster.mgmanager.saic.ServerStatus
import dk.Android.Cpmonster.mgmanager.data.model.CarColor
import dk.Android.Cpmonster.mgmanager.ui.car.BodyStyle
import dk.Android.Cpmonster.mgmanager.ui.car.CarSilhouette
import dk.Android.Cpmonster.mgmanager.ui.car.carColor
import dk.Android.Cpmonster.mgmanager.ui.car.rememberCarPhoto
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date

val ColdColor = Color(0xFF2F6FEB)
val WarmColor = Color(0xFFE8590C)
private val ColdDeep = Color(0xFF1B3F9E)
private val WarmDeep = Color(0xFFB23A06)

data class HomeUi(
    val vehicle: VehicleState? = null,
    val profiles: List<Profile> = emptyList(),
    val activeId: Long? = null,
    val active: ActiveProfile? = null,
    val suggestion: Profile? = null,
    /** Fetching car data from MG. */
    val syncing: Boolean = false,
    val notice: String? = null,
    val carColorOverride: CarColor? = null,
    val carPhoto: Long? = null,
    /** The heating/A/C command is on its way to the car (waiting for the car, not for MG). */
    val sending: Boolean = false,
    /** e.g. "Seat heating and steering wheel on the way…", null when nothing extra is waiting. */
    val extras: String? = null,
)

private data class QueueView(val refreshing: Boolean, val sending: Boolean, val dropped: String?, val extras: Set<JobType>)

/** Small line for extras still waiting for the car. */
internal fun extrasText(waiting: Set<JobType>): String? {
    val names = listOfNotNull(
        "seat heating".takeIf { JobType.SEATS in waiting },
        "steering wheel".takeIf { JobType.STEERING_WHEEL in waiting },
        "rear window".takeIf { JobType.REAR_WINDOW in waiting },
    )
    if (names.isEmpty()) return null
    val list = if (names.size == 1) names[0] else names.dropLast(1).joinToString(", ") + " and " + names.last()
    return list.replaceFirstChar { it.uppercase() } + " on the way…"
}

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val dismissedFor = MutableStateFlow<Long?>(null)

    val ui: StateFlow<HomeUi> = combine(
        combine(c.vehicle.state, c.settings.carColor, c.settings.carPhoto) { v, o, p -> Triple(v, o, p) },
        c.profiles.profiles,
        c.profiles.active,
        combine(c.queue.refreshing, c.queue.sending, c.queue.lastDropped, c.queue.extrasWaiting) { r, s, d, x -> QueueView(r, s, d, x) },
        dismissedFor,
    ) { (vehicle, colorOverride, photo), profiles, active, q, dismissed ->
        val suggestion = ProfileSuggester.suggest(vehicle?.exteriorTemp, profiles, active?.id)
            ?.takeIf { it.id != dismissed }
        HomeUi(vehicle, profiles, active?.id, active, suggestion, q.refreshing, q.dropped, colorOverride, photo, q.sending, extrasText(q.extras))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    init {
        viewModelScope.launch { c.controller.resume() }
    }

    fun setActive(profile: Profile, on: Boolean) = viewModelScope.launch {
        if (on) c.controller.activate(profile) else c.controller.deactivate()
    }

    fun accept(profile: Profile) = setActive(profile, true)

    fun dismiss(profile: Profile) {
        dismissedFor.value = profile.id
    }

    fun refresh() = viewModelScope.launch { c.queue.enqueue(JobType.REFRESH_STATUS) }

    /**
     * How MG's servers are doing, for the status ring. Null in demo mode (nothing is sent).
     * Read here rather than passed in: NavDisplay keeps a screen's content per key, so
     * parameters from outside wouldn't update.
     */
    val server: StateFlow<ServerSnapshot?> = combine(ServerStatus.state, c.auth.state) { s, a ->
        if (a.isDemo) null else s
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** "Next start in 1 day 4 hours · Auto", or null when no schedule is on. Ticks every minute. */
    val nextStart: StateFlow<String?> = combine(
        c.scheduler.schedule,
        c.profiles.profiles,
        kotlinx.coroutines.flow.flow { while (true) { emit(Unit); delay(60_000) } },
    ) { s, profiles, _ ->
        val now = java.time.ZonedDateTime.now()
        s.nextStart(now)?.let { at ->
            val name = s.profileId?.let { id -> profiles.firstOrNull { it.id == id }?.name } ?: "Auto"
            "Next start in ${dk.Android.Cpmonster.mgmanager.schedule.timeUntil(now, at)} · $name"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onEditProfile: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isDemo: Boolean = false,
    onExitDemo: () -> Unit = {},
    onOpenSchedule: () -> Unit = {},
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val nextStart by viewModel.nextStart.collectAsStateWithLifecycle()
    val server by viewModel.server.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // A session may already be running – a status refresh re-posts its notification.
        if (granted) viewModel.refresh()
    }
    // The running-climate notification needs permission on Android 13+. Ask the first time it matters.
    val askForNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val activeProfile = ui.profiles.firstOrNull { it.id == ui.activeId }
    val accent = (activeProfile ?: ui.suggestion)?.kind?.color() ?: MaterialTheme.colorScheme.primary

    // Pull down to refresh. The pull spinner only shows briefly to confirm the gesture;
    // the status ring next to the car then shows how the request is going.
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(pulled) {
        if (pulled) {
            delay(900)
            pulled = false
        }
    }
    PullToRefreshBox(
        isRefreshing = pulled,
        onRefresh = {
            pulled = true
            viewModel.refresh()
        },
        modifier = modifier.fillMaxSize(),
    ) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Landscape / wide screens: car on the left, suggestion and profiles on the right,
        // each scrolling on its own. One full-width card looked stretched and pushed the profiles off-screen.
        val wide = dk.Android.Cpmonster.mgmanager.ui.components.isWideLayout()
        val carPart: @Composable ColumnScope.() -> Unit = {
            if (isDemo) DemoBanner(onExitDemo)
            HeroCard(ui.vehicle, ui.syncing, ui.sending, ui.extras, ui.notice, activeProfile, accent, carColor(ui.carColorOverride, ui.vehicle), rememberCarPhoto(ui.carPhoto), server, onRefresh = { viewModel.refresh() })
        }
        val profilesPart: @Composable ColumnScope.() -> Unit = {
            AnimatedVisibility(visible = ui.suggestion != null) {
                ui.suggestion?.let { s ->
                    SuggestionCard(
                        profile = s,
                        outsideTemp = ui.vehicle?.exteriorTemp,
                        onAccept = { askForNotifications(); viewModel.accept(s) },
                    )
                }
            }

            Column(Modifier.padding(top = 6.dp, start = 4.dp)) {
                Text("A/C profiles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Tap to start or stop · hold to edit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                nextStart?.let { text ->
                    Row(
                        Modifier.padding(top = 4.dp).clickable(onClick = onOpenSchedule),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.AlarmOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (ui.profiles.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            // Profiles side by side, two per row (quick-settings style).
            ui.profiles.chunked(2).forEach { pair ->
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    pair.forEach { p ->
                        ProfileTile(
                            profile = p,
                            active = p.id == ui.activeId,
                            session = ui.active?.takeIf { it.id == p.id },
                            onToggle = { on -> if (on) askForNotifications(); viewModel.setActive(p, on) },
                            onEdit = { onEditProfile(p.id) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (wide) {
            Row(
                Modifier
                    .fillMaxSize()
                    // Keep clear of a camera cutout on the side in landscape.
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = carPart,
                )
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = profilesPart,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                carPart()
                profilesPart()
            }
        }
    }
    }
}

@Composable
private fun DemoBanner(onExit: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Demo mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Pretend car – nothing is sent anywhere.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onExit) { Text("Log in") }
        }
    }
}

@Composable
private fun HeroCard(
    v: VehicleState?,
    syncing: Boolean,
    sending: Boolean,
    extras: String?,
    notice: String?,
    activeProfile: Profile?,
    accent: Color,
    paint: CarColor,
    photo: ImageBitmap?,
    server: ServerSnapshot?,
    onRefresh: () -> Unit,
) {
    val tint by animateColorAsState(accent, label = "hero")
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.45f), tint.copy(alpha = 0.12f), base)))
            .padding(20.dp),
    ) {
        // Faded picture of the car (own photo, else our drawing), fading out towards the text on the left.
        val fade = Modifier
            .graphicsLayer { alpha = if (photo != null) 0.5f else 0.6f; compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.horizontalGradient(0f to Color.Transparent, 0.5f to Color.Black),
                    blendMode = BlendMode.DstIn,
                )
            }
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
            if (photo != null) {
                Image(
                    photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(0.8f).offset(x = 20.dp).then(fade),
                )
            } else {
                CarSilhouette(
                    style = BodyStyle.of(v),
                    color = paint,
                    modifier = Modifier.fillMaxWidth(0.72f).offset(x = 44.dp, y = 4.dp).then(fade),
                )
            }
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.DirectionsCar, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                val shown = rememberShownServerState(server, syncing)
                Column(Modifier.weight(1f)) {
                    Text(v?.model ?: "Your MG", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    val sub = when {
                        sending -> "Sending to car…"
                        syncing -> "Updating…"
                        v?.updatedAt != null -> "Updated " + DateUtils.getRelativeTimeSpanString(
                            v.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                        )
                        else -> "Waiting for car data"
                    }
                    Text(
                        listOfNotNull(v?.modelYear, sub).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AnimatedVisibility(visible = shown.message != null) {
                        Text(
                            shown.message.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = shown.health.statusColor(MaterialTheme.colorScheme.onSurfaceVariant),
                        )
                    }
                }
                ServerStatusRing(shown, onRefresh = onRefresh)
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    v?.exteriorTemp?.let { "$it°" } ?: "–",
                    fontSize = 64.sp,
                    lineHeight = 64.sp,
                    fontWeight = FontWeight.Light,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.padding(bottom = 10.dp)) {
                    Text("outside", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        when {
                            activeProfile != null -> if (activeProfile.kind == ProfileKind.COLD) "Warming up the cabin" else "Cooling down the cabin"
                            else -> "A/C is off"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill(Icons.Rounded.Thermostat, "Inside", v?.interiorTemp?.let { "$it°" }, null, Modifier.weight(1f))
                StatPill(Icons.Rounded.BatteryStd, "Battery", v?.batterySoc?.let { "${it.toInt()}%" }, v?.batterySoc?.let { it.toFloat() / 100f }, Modifier.weight(1f))
            }
            AnimatedVisibility(visible = extras != null && notice == null) {
                Text(
                    extras.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            notice?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun StatPill(icon: ImageVector, label: String, value: String?, progress: Float?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(value ?: "–", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (progress != null) {
                Spacer(Modifier.height(6.dp))
                val p by animateFloatAsState(progress.coerceIn(0f, 1f), label = "soc")
                LinearProgressIndicator(
                    progress = { p },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            } else {
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun SuggestionCard(profile: Profile, outsideTemp: Int?, onAccept: () -> Unit) {
    val color = profile.kind.color()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.14f),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(profile.kind.icon(), null, tint = color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("It's ${outsideTemp ?: "–"}° outside", style = MaterialTheme.typography.titleSmall)
                Text("${profile.name} the car?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledIconButton(
                onClick = onAccept,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = Color.White),
                modifier = Modifier.size(48.dp),
            ) { Icon(Icons.Rounded.PlayArrow, "Start ${profile.name}") }
        }
    }
}

/**
 * One button per profile, like Google Home / quick-settings tiles:
 * tap = start/stop, long-press or the small settings icon inside the tile = edit.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileTile(
    profile: Profile,
    active: Boolean,
    session: ActiveProfile?,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val color = profile.kind.color()
    val deep = profile.kind.deepColor()
    val idle = MaterialTheme.colorScheme.surfaceContainer
    val start by animateColorAsState(if (active) color else idle, label = "tileStart")
    val end by animateColorAsState(if (active) deep else idle, label = "tileEnd")
    val content = if (active) Color.White else MaterialTheme.colorScheme.onSurface

    // Running: the tile glows in the profile's colour, with a slow "breathing" light behind the icon
    // and a soft sheen drifting across. Off: plain tile, tinted icon. The whole tile is the toggle.
    val pulse = rememberInfiniteTransition(label = "tilePulse")
    val breath by pulse.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath",
    )
    val sheen by pulse.animateFloat(
        -0.4f, 1.4f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "sheen",
    )
    val glow by animateFloatAsState(if (active) 1f else 0f, label = "glow")

    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(start, end)))
            .drawWithContent {
                drawContent()
                if (glow > 0f) {
                    val x = size.width * sheen
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.16f * glow), Color.Transparent),
                            start = Offset(x - size.width * 0.25f, 0f),
                            end = Offset(x + size.width * 0.25f, size.height),
                        ),
                    )
                }
            }
            .combinedClickable(
                role = Role.Switch,
                onClickLabel = if (active) "Stop" else "Start",
                onLongClickLabel = "Edit",
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEdit()
                },
                onClick = { onToggle(!active) },
            )
            .semantics {
                contentDescription = profile.name
                stateDescription = if (active) "On" else "Off"
            },
    ) {
      Column {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(44.dp)
                        .graphicsLayer {
                            val s = 1f + 0.12f * breath * glow
                            scaleX = s
                            scaleY = s
                        }
                        .clip(CircleShape)
                        .background(
                            if (active) Color.White.copy(alpha = 0.18f + 0.14f * breath * glow)
                            else color.copy(alpha = 0.15f),
                        ),
                )
                Icon(profile.kind.icon(), null, tint = if (active) Color.White else color, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Rounded.Tune,
                    contentDescription = "Edit ${profile.name}",
                    tint = if (active) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (session != null) {
            val until = DateFormat.getTimeFormat(context).format(Date(session.sessionEndsAt))
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.22f),
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                Text(
                    "${session.run}/${session.runs} · until $until",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = content,
                    maxLines = 1,
                )
            }
        }
      }
    }
}

// COLD = "Warm up" (heats the car), WARM = "Chill" (cools it): colour/icon show what the profile does.
fun ProfileKind.color() = if (this == ProfileKind.COLD) WarmColor else ColdColor
private fun ProfileKind.deepColor() = if (this == ProfileKind.COLD) WarmDeep else ColdDeep
fun ProfileKind.icon() = if (this == ProfileKind.COLD) Icons.Rounded.LocalFireDepartment else Icons.Rounded.AcUnit

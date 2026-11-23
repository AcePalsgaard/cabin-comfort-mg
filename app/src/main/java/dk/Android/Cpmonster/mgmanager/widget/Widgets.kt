package dk.Android.Cpmonster.mgmanager.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import dk.Android.Cpmonster.mgmanager.AppContainer
import dk.Android.Cpmonster.mgmanager.MGApp
import dk.Android.Cpmonster.mgmanager.R
import dk.Android.Cpmonster.mgmanager.data.db.Profile
import dk.Android.Cpmonster.mgmanager.data.db.ProfileKind
import dk.Android.Cpmonster.mgmanager.data.profile.ProfileSuggester
import dk.Android.Cpmonster.mgmanager.queue.JobType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Home screen widgets. Both show which profile (if any) is running.
 * - Auto: starts the profile that fits the outside temperature; tap again to stop.
 * - Profiles: the cold and the warm profile side by side, tap to start/stop.
 * Kept up to date by [AppContainer] collecting [snapshots].
 */
object Widgets {
    const val ACTION_AUTO = "dk.Android.Cpmonster.mgmanager.widget.AUTO"
    const val ACTION_TOGGLE = "dk.Android.Cpmonster.mgmanager.widget.TOGGLE"
    private const val EXTRA_PROFILE = "profile_id"

    data class Snapshot(
        val loggedIn: Boolean,
        val profiles: List<Profile>,
        val activeId: Long?,
        val outsideTemp: Int?,
    ) {
        val cold get() = profiles.firstOrNull { it.kind == ProfileKind.COLD }
        val warm get() = profiles.firstOrNull { it.kind == ProfileKind.WARM }
        val active get() = profiles.firstOrNull { it.id == activeId }
    }

    fun snapshots(c: AppContainer): Flow<Snapshot> =
        combine(c.auth.state, c.profiles.profiles, c.profiles.active, c.vehicle.state) { auth, profiles, active, v ->
            Snapshot(auth.isLoggedIn, profiles, active?.id, v?.exteriorTemp)
        }.distinctUntilChanged()

    /** Profile the Auto button starts: same thresholds as the suggestion, split at the midpoint in between. */
    fun autoPick(s: Snapshot): Profile? {
        val t = s.outsideTemp ?: return null
        val mid = (ProfileSuggester.COLD_BELOW + ProfileSuggester.WARM_ABOVE) / 2
        return if (t <= mid) s.cold else s.warm
    }

    suspend fun handle(c: AppContainer, intent: Intent) {
        val s = snapshots(c).first()
        if (!s.loggedIn) return
        when (intent.action) {
            ACTION_AUTO -> when {
                s.activeId != null -> c.controller.deactivate()
                else -> autoPick(s)?.let { c.controller.activate(it) } ?: c.queue.enqueue(JobType.REFRESH_STATUS)
            }
            ACTION_TOGGLE -> {
                val p = s.profiles.firstOrNull { it.id == intent.getLongExtra(EXTRA_PROFILE, -1) } ?: return
                if (s.activeId == p.id) c.controller.deactivate() else c.controller.activate(p)
            }
        }
    }

    fun render(context: Context, s: Snapshot) {
        val mgr = AppWidgetManager.getInstance(context)
        mgr.getAppWidgetIds(ComponentName(context, AutoWidget::class.java))
            .takeIf { it.isNotEmpty() }?.let { mgr.updateAppWidget(it, autoViews(context, s)) }
        mgr.getAppWidgetIds(ComponentName(context, ProfilesWidget::class.java))
            .takeIf { it.isNotEmpty() }?.let { mgr.updateAppWidget(it, profilesViews(context, s)) }
    }

    // ---- Views ----

    private class Button(val root: Int, val icon: Int, val title: Int, val sub: Int)

    private val AUTO = Button(R.id.auto_btn, R.id.auto_icon, R.id.auto_title, R.id.auto_sub)
    private val COLD = Button(R.id.cold_btn, R.id.cold_icon, R.id.cold_title, R.id.cold_sub)
    private val WARM = Button(R.id.warm_btn, R.id.warm_icon, R.id.warm_title, R.id.warm_sub)

    private fun autoViews(ctx: Context, s: Snapshot): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_auto)
        val active = s.active
        val pick = autoPick(s)
        when {
            !s.loggedIn -> {
                paint(ctx, v, AUTO, on = false, kind = null, title = "Auto", sub = "Sign in")
                v.setOnClickPendingIntent(AUTO.root, openApp(ctx))
            }
            active != null -> {
                paint(ctx, v, AUTO, on = true, kind = active.kind, title = active.name, sub = "On · tap to stop")
                v.setOnClickPendingIntent(AUTO.root, broadcast(ctx, AutoWidget::class.java, ACTION_AUTO, 0))
            }
            pick != null -> {
                paint(ctx, v, AUTO, on = false, kind = pick.kind, title = "Auto", sub = "${s.outsideTemp}° → ${pick.name}")
                v.setOnClickPendingIntent(AUTO.root, broadcast(ctx, AutoWidget::class.java, ACTION_AUTO, 0))
            }
            else -> {
                paint(ctx, v, AUTO, on = false, kind = null, title = "Auto", sub = "Waiting for car")
                v.setOnClickPendingIntent(AUTO.root, broadcast(ctx, AutoWidget::class.java, ACTION_AUTO, 0))
            }
        }
        return v
    }

    private fun profilesViews(ctx: Context, s: Snapshot): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_profiles)
        listOf(COLD to s.cold, WARM to s.warm).forEach { (b, p) ->
            if (p == null) {
                v.setViewVisibility(b.root, View.GONE)
                return@forEach
            }
            v.setViewVisibility(b.root, View.VISIBLE)
            val on = p.id == s.activeId
            val sub = when {
                !s.loggedIn -> "Sign in"
                on -> "On · tap to stop"
                else -> "Off"
            }
            paint(ctx, v, b, on, p.kind, p.name, sub)
            v.setOnClickPendingIntent(
                b.root,
                if (s.loggedIn) broadcast(ctx, ProfilesWidget::class.java, ACTION_TOGGLE, p.id) else openApp(ctx),
            )
        }
        return v
    }

    private fun paint(ctx: Context, v: RemoteViews, b: Button, on: Boolean, kind: ProfileKind?, title: String, sub: String) {
        val kindColor = when (kind) {
            ProfileKind.COLD -> ctx.getColor(R.color.widget_heat)
            ProfileKind.WARM -> ctx.getColor(R.color.widget_cool)
            null -> ctx.getColor(R.color.widget_text)
        }
        val bg = when {
            !on -> R.drawable.widget_btn_off
            kind == ProfileKind.COLD -> R.drawable.widget_btn_heat
            else -> R.drawable.widget_btn_cool
        }
        val icon = when (kind) {
            ProfileKind.COLD -> R.drawable.ic_widget_heat
            ProfileKind.WARM -> R.drawable.ic_widget_cool
            null -> R.drawable.ic_stat_climate
        }
        val white = ctx.getColor(R.color.white)
        v.setInt(b.root, "setBackgroundResource", bg)
        v.setImageViewResource(b.icon, icon)
        v.setInt(b.icon, "setColorFilter", if (on) white else kindColor)
        v.setTextViewText(b.title, title)
        v.setTextColor(b.title, if (on) white else ctx.getColor(R.color.widget_text))
        v.setTextViewText(b.sub, sub)
        v.setTextColor(b.sub, if (on) white else ctx.getColor(R.color.widget_text_dim))
    }

    private fun broadcast(ctx: Context, cls: Class<*>, action: String, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            (action.hashCode() * 31 + id).toInt(),
            Intent(ctx, cls).setAction(action).putExtra(EXTRA_PROFILE, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openApp(ctx: Context): PendingIntent? =
        ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
            PendingIntent.getActivity(ctx, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
}

/** Shared receiver logic: redraw on update, run taps in the background. */
abstract class BaseWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Widgets.ACTION_AUTO && intent.action != Widgets.ACTION_TOGGLE) {
            return super.onReceive(context, intent)
        }
        val c = (context.applicationContext as MGApp).container
        val done = goAsync()
        c.scope.launch {
            try {
                Widgets.handle(c, intent)
            } finally {
                done.finish()
            }
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val c = (context.applicationContext as MGApp).container
        val done = goAsync()
        c.scope.launch {
            try {
                Widgets.render(context, Widgets.snapshots(c).first())
            } finally {
                done.finish()
            }
        }
    }
}

class AutoWidget : BaseWidget()
class ProfilesWidget : BaseWidget()

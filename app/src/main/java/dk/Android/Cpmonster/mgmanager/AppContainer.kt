package dk.Android.Cpmonster.mgmanager

import android.app.Application
import android.content.Context
import dk.Android.Cpmonster.mgmanager.data.auth.AuthRepository
import dk.Android.Cpmonster.mgmanager.data.db.AppDatabase
import dk.Android.Cpmonster.mgmanager.data.profile.ProfileRepository
import dk.Android.Cpmonster.mgmanager.data.repository.SettingsRepositoryImpl
import dk.Android.Cpmonster.mgmanager.data.repository.dataStore
import dk.Android.Cpmonster.mgmanager.data.vehicle.VehicleRepository
import dk.Android.Cpmonster.mgmanager.queue.CommandQueue
import dk.Android.Cpmonster.mgmanager.queue.JobType
import dk.Android.Cpmonster.mgmanager.queue.ProfileController
import dk.Android.Cpmonster.mgmanager.work.StatusPollWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Simple manual DI. */
class AppContainer(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val db = AppDatabase.create(context)

    val settings = SettingsRepositoryImpl(context.dataStore)
    val auth = AuthRepository(context, http, settings)
    val vehicle = VehicleRepository(db.vehicle())
    val profiles = ProfileRepository(context, db.profiles())
    private val demoCar = dk.Android.Cpmonster.mgmanager.demo.DemoCar(vehicle)

    val queue: CommandQueue = CommandQueue(
        context = context,
        dao = db.jobs(),
        auth = auth,
        vehicle = vehicle,
        scope = scope,
        afterRefresh = { controller.reconcile(it) },
        demo = demoCar,
    )

    val controller = ProfileController(context, profiles, vehicle, queue)

    /** Called once at startup: everything here runs in the background. */
    fun onAppStart() {
        dk.Android.Cpmonster.mgmanager.climate.ClimateSession.createChannels(context)
        scope.launch {
            profiles.seedDefaults()
            controller.resume()
            if (auth.isLoggedIn()) {
                queue.enqueue(JobType.REFRESH_STATUS)
            }
            queue.kick()
        }
        StatusPollWorker.schedule(context)
        watchConnectivity()
        // MG's servers back after an outage: send what's waiting right away.
        scope.launch {
            var wasDown = false
            dk.Android.Cpmonster.mgmanager.saic.ServerStatus.state.collect { s ->
                val h = s.health
                val failed = h == dk.Android.Cpmonster.mgmanager.saic.ServerHealth.DOWN ||
                    h == dk.Android.Cpmonster.mgmanager.saic.ServerHealth.OFFLINE
                if (!failed && h != dk.Android.Cpmonster.mgmanager.saic.ServerHealth.UNKNOWN && wasDown) queue.retryNow()
                wasDown = failed
            }
        }
        // Home screen widgets follow the active profile, profile names and outside temp.
        scope.launch {
            dk.Android.Cpmonster.mgmanager.widget.Widgets.snapshots(this@AppContainer)
                .collect { dk.Android.Cpmonster.mgmanager.widget.Widgets.render(context, it) }
        }
    }

    /** Tells ServerStatus whether the phone is online, and retries at once when it comes back. */
    private fun watchConnectivity() {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return
        fun online(n: android.net.Network?) = n != null && cm.getNetworkCapabilities(n)
            ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        dk.Android.Cpmonster.mgmanager.saic.ServerStatus.setPhoneOnline(online(cm.activeNetwork))
        cm.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                val wasOffline = !dk.Android.Cpmonster.mgmanager.saic.ServerStatus.state.value.phoneOnline
                dk.Android.Cpmonster.mgmanager.saic.ServerStatus.setPhoneOnline(true)
                if (wasOffline) scope.launch { queue.retryNow() }
            }
            override fun onLost(network: android.net.Network) {
                dk.Android.Cpmonster.mgmanager.saic.ServerStatus.setPhoneOnline(false)
            }
        })
    }

    suspend fun onLoggedIn() {
        vehicle.clear()
        queue.enqueue(JobType.REFRESH_STATUS)
    }

    /** Guest mode: try the app with a pretend car. Nothing is sent anywhere. */
    suspend fun startDemo() {
        queue.clear()
        vehicle.clear()
        demoCar.reset()
        auth.startDemo()
        dk.Android.Cpmonster.mgmanager.saic.ServerStatus.reset()
        queue.enqueue(JobType.REFRESH_STATUS)
    }

    /** Switch MG server. Credentials belong to one server, so this logs out when it changes. */
    suspend fun changeServer(region: dk.Android.Cpmonster.mgmanager.saic.SaicRegion) {
        if (auth.state.first().region == region) return
        if (auth.state.first().let { it.hasCredentials || it.isDemo }) logout()
        auth.setRegion(region)
    }

    suspend fun logout() {
        auth.logout()
        queue.clear()
        profiles.clearActive()
        dk.Android.Cpmonster.mgmanager.climate.ClimateSession.cancelAlarms(context)
        dk.Android.Cpmonster.mgmanager.climate.ClimateSession.clearAll(context)
        vehicle.clear()
        demoCar.reset()
        dk.Android.Cpmonster.mgmanager.saic.ServerStatus.reset()
    }
}

class MGApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.onAppStart()
    }
}

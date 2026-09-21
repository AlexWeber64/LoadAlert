package com.loadalert.di

import android.content.Context
import com.loadalert.LoadAlertApplication
import com.loadalert.data.cycle.CycleStateRepository
import com.loadalert.data.history.HistoryRepository
import com.loadalert.data.history.LoadAlertDatabase
import com.loadalert.data.settings.SettingsRepository
import com.loadalert.domain.CycleCoordinator
import com.loadalert.location.GeofenceManager
import com.loadalert.location.LocationClient
import com.loadalert.messaging.RoommateMessenger
import com.loadalert.notifications.NotificationHelper
import com.loadalert.work.CycleScheduler

/**
 * Hand-rolled service locator.
 *
 * Workers and broadcast receivers are instantiated by the framework, so they cannot take
 * constructor dependencies. A container hung off the Application gives them one lookup point
 * without pulling in a DI framework and its code generation.
 */
class AppContainer(private val appContext: Context) {

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val cycleRepository: CycleStateRepository by lazy { CycleStateRepository(appContext) }

    val historyRepository: HistoryRepository by lazy {
        HistoryRepository(LoadAlertDatabase.get(appContext).cycleHistoryDao())
    }

    val locationClient: LocationClient by lazy { LocationClient(appContext) }

    val geofenceManager: GeofenceManager by lazy { GeofenceManager(appContext) }

    val notificationHelper: NotificationHelper by lazy { NotificationHelper(appContext) }

    val cycleScheduler: CycleScheduler by lazy { CycleScheduler(appContext) }

    val roommateMessenger: RoommateMessenger by lazy { RoommateMessenger(appContext) }

    val cycleCoordinator: CycleCoordinator by lazy {
        CycleCoordinator(
            appContext = appContext,
            settingsRepository = settingsRepository,
            cycleRepository = cycleRepository,
            historyRepository = historyRepository,
            locationClient = locationClient,
            geofenceManager = geofenceManager,
            notificationHelper = notificationHelper,
            scheduler = cycleScheduler,
        )
    }
}

/**
 * Reaches the container from anywhere with a Context. Falls back to building a detached
 * container so widget and receiver code still works if the Application subclass is replaced
 * (which instrumentation runners do).
 */
val Context.container: AppContainer
    get() = when (val app = applicationContext) {
        is LoadAlertApplication -> app.container
        else -> AppContainer(applicationContext)
    }

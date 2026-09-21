package com.loadalert

import android.app.Application
import com.loadalert.di.AppContainer

class LoadAlertApplication : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Channels must exist before the first notification, and the first one can come from
        // a worker in a process the user never opened the UI in.
        container.notificationHelper.createChannels()
    }
}

package com.loadalert.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.loadalert.data.model.CycleState
import com.loadalert.di.container
import com.loadalert.ui.MainActivity
import java.util.concurrent.TimeUnit

/**
 * Home-screen widget. One tap starts a load with the saved default duration.
 *
 * When a roommate is configured the tap opens the app instead of starting in place, because
 * the outgoing message has to be launched as an Activity and background activity starts are
 * blocked from a widget on Android 10 and up.
 */
class StartLaundryWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val cycle = context.container.cycleRepository.current()
        provideContent {
            GlanceTheme {
                WidgetBody(cycle)
            }
        }
    }

    @Composable
    private fun WidgetBody(cycle: CycleState) {
        val running = cycle.isActive
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
                .clickable(
                    if (running) {
                        actionStartActivity<MainActivity>()
                    } else {
                        actionRunCallback<StartLaundryAction>()
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "LoadAlert",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                ),
            )
            Text(
                text = if (running) statusLine(cycle) else "Start Laundry",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }

    private fun statusLine(cycle: CycleState): String {
        val remaining = cycle.millisRemaining()
        if (remaining <= 0L) return "Ready - go grab it"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) + 1
        return "$minutes min left"
    }

    companion object {
        suspend fun refresh(context: Context) {
            try {
                StartLaundryWidget().updateAll(context)
            } catch (e: Exception) {
                // No widget placed, or the host is unavailable. Nothing to update.
            }
        }
    }
}

/** Starts a cycle straight from the home screen. */
class StartLaundryAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val container = context.container
        val settings = container.settingsRepository.current()

        if (settings.laundryLocation == null || settings.roommate != null) {
            // Setup is incomplete, or we need an Activity to launch the roommate message.
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_START_CYCLE_NOW, true),
            )
            return
        }

        container.cycleCoordinator.startCycle()
        StartLaundryWidget().updateAll(context)
    }
}

package com.loadalert.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.loadalert.ui.theme.LoadAlertTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val requestedStart = intent?.getBooleanExtra(EXTRA_START_CYCLE_NOW, false) == true

        setContent {
            // The widget can ask us to start a cycle the moment the UI comes up. Held in state
            // so it survives recomposition but fires exactly once.
            var startNow by remember { mutableStateOf(requestedStart) }
            LoadAlertTheme {
                LoadAlertApp(
                    startCycleNow = startNow,
                    onStartCycleHandled = { startNow = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    companion object {
        const val EXTRA_START_CYCLE_NOW = "com.loadalert.extra.START_CYCLE_NOW"
    }
}

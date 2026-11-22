package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.data.remote.configureAppCheck
import com.example.ui.DexCommandApp
import com.example.worker.ReviewAuditWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        // STEP 2: Enable dark edge-to-edge system chrome matching deep charcoal command canvas
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        // STEP 3: Schedule offline-first periodic WorkManager workspace audit
        ReviewAuditWorker.schedulePeriodic(applicationContext)

        // STEP 4: Render Compose UI
        setContent {
            DexCommandApp()
        }
    }
}

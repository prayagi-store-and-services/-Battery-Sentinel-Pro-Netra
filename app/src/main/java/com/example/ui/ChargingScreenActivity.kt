package com.example.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.ui.components.ChargingScreenContent

/** Opened by the charger-connected event when the user allowed it. Black screen, real values only, tap to close. */
class ChargingScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ChargingScreenContent(onClose = { finish() }) }
    }
}

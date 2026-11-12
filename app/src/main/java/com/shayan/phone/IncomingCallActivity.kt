package com.shayan.phone

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect

/**
 * Dedicated UI target for Telecom's incoming-call full-screen notification.
 * It is separate from the launcher activity so it can be opened while the app
 * process has no visible activity.
 */
class IncomingCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        setContent {
            ShayanPhoneTheme {
                val call = ActiveCall.current
                if (call == null) {
                    LaunchedEffect(Unit) { finish() }
                } else {
                    InCallPanel(call, onFinish = {
                        finish()
                    })
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

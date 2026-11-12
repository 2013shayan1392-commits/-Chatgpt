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
        PhoneErrorLog.add(this, "INCOMING_ACTIVITY", "onCreate; callPresent=" + (ActiveCall.current != null))
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            ShayanPhoneTheme {
                val call = ActiveCall.current
                if (call == null) {
                    PhoneErrorLog.add(this@IncomingCallActivity, "INCOMING_ACTIVITY", "No active call on UI creation")
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

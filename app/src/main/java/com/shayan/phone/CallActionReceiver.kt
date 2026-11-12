package com.shayan.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.VideoProfile

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val call = ActiveCall.current ?: return
        when (intent.action) {
            ACTION_ANSWER -> {
                if (call.state == android.telecom.Call.STATE_RINGING) {
                    call.answer(VideoProfile.STATE_AUDIO_ONLY)
                }
                PhoneInCallService.dismissIncomingNotification(context)
                val activity = Intent(context, IncomingCallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(activity)
            }
            ACTION_REJECT -> {
                if (call.state == android.telecom.Call.STATE_RINGING) {
                    call.reject(false, null)
                }
                PhoneInCallService.dismissIncomingNotification(context)
            }
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.shayan.phone.action.ANSWER"
        const val ACTION_REJECT = "com.shayan.phone.action.REJECT"
    }
}

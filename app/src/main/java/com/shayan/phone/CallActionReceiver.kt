package com.shayan.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.VideoProfile

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PhoneErrorLog.add(context, "CALL_ACTION", "action=" + intent.action)
        val call = ActiveCall.current ?: run {
            PhoneErrorLog.add(context, "CALL_ACTION", "No active call found")
            return
        }
        when (intent.action) {
            ACTION_ANSWER -> {
                if (call.state == android.telecom.Call.STATE_RINGING) {
                    runCatching { call.answer(VideoProfile.STATE_AUDIO_ONLY) }
                        .onFailure { PhoneErrorLog.exception(context, "ANSWER_CALL_FAILED", it) }
                }
                PhoneInCallService.dismissIncomingNotification(context)
                val activity = Intent(context, IncomingCallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(activity)
            }
            ACTION_REJECT -> {
                if (call.state == android.telecom.Call.STATE_RINGING) {
                    runCatching { call.reject(false, null) }
                        .onFailure { PhoneErrorLog.exception(context, "REJECT_CALL_FAILED", it) }
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

package com.shayan.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf

class PhoneInCallService : InCallService() {
    private val callCallbacks = mutableMapOf<Call, Call.Callback>()

    override fun onCreate() {
        super.onCreate()
        ActiveCall.service = this
        PhoneErrorLog.add(this, "IN_CALL_SERVICE", "onCreate")
        createIncomingCallChannel()
    }

    override fun onCallAudioStateChanged(audioState: android.telecom.CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        ActiveCall.muted.value = audioState.isMuted
        ActiveCall.audioRoute.value = audioState.route
        PhoneErrorLog.add(this, "CALL_AUDIO", "muted=" + audioState.isMuted + "; route=" + audioState.route)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        if (!ActiveCall.calls.contains(call)) ActiveCall.calls.add(call)
        ActiveCall.current = call
        PhoneErrorLog.add(this, "CALL_ADDED", "state=" + call.state + "; trackedCalls=" + ActiveCall.calls.size + "; sdk=" + Build.VERSION.SDK_INT)

        val callback = object : Call.Callback() {
            override fun onStateChanged(changedCall: Call, state: Int) {
                PhoneErrorLog.add(this@PhoneInCallService, "CALL_STATE", "state=" + state)
                if (state == Call.STATE_RINGING) {
                    showIncomingCallNotification(changedCall)
                } else {
                    dismissIncomingNotification(this@PhoneInCallService)
                }
            }
        }
        callCallbacks[call] = callback
        call.registerCallback(callback)

        if (call.state == Call.STATE_RINGING) {
            showIncomingCallNotification(call)
        }
    }

    override fun onCallRemoved(call: Call) {
        callCallbacks.remove(call)?.let { call.unregisterCallback(it) }
        ActiveCall.calls.remove(call)
        PhoneErrorLog.add(this, "CALL_REMOVED", "state=" + call.state + "; trackedCalls=" + ActiveCall.calls.size)
        if (ActiveCall.current == call) ActiveCall.current = ActiveCall.calls.lastOrNull { it.state != Call.STATE_DISCONNECTED }
        dismissIncomingNotification(this)
        super.onCallRemoved(call)
    }

    override fun onDestroy() {
        PhoneErrorLog.add(this, "IN_CALL_SERVICE", "onDestroy")
        callCallbacks.forEach { (call, callback) -> runCatching { call.unregisterCallback(callback) } }
        callCallbacks.clear()
        if (ActiveCall.service === this) ActiveCall.service = null
        super.onDestroy()
    }

    private fun createIncomingCallChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            "تماس‌های ورودی",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "اعلان و زنگ تماس‌های ورودی"
            setSound(ringtone, audioAttributes)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    private fun showIncomingCallNotification(call: Call) {
        val manager = getSystemService(NotificationManager::class.java) ?: run {
            PhoneErrorLog.add(this, "INCOMING_NOTIFICATION", "NotificationManager unavailable")
            return
        }
        val number = call.details.handle?.schemeSpecificPart ?: "شماره ناشناس"
        val callerName = call.details.callerDisplayName?.takeIf { it.isNotBlank() } ?: number

        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_FULLSCREEN,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerIntent = PendingIntent.getBroadcast(
            this,
            REQUEST_ANSWER,
            Intent(this, CallActionReceiver::class.java).setAction(CallActionReceiver.ACTION_ANSWER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val rejectIntent = PendingIntent.getBroadcast(
            this,
            REQUEST_REJECT,
            Intent(this, CallActionReceiver::class.java).setAction(CallActionReceiver.ACTION_REJECT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(callerName)
            .setContentText("تماس ورودی")
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setPriority(Notification.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.sym_action_call,
                    "پاسخ",
                    answerIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "رد تماس",
                    rejectIntent
                ).build()
            )
            .build()

        manager.notify(INCOMING_NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "incoming_calls"
        private const val INCOMING_NOTIFICATION_ID = 4101
        private const val REQUEST_FULLSCREEN = 4102
        private const val REQUEST_ANSWER = 4103
        private const val REQUEST_REJECT = 4104

        fun dismissIncomingNotification(context: Context) {
            context.getSystemService(NotificationManager::class.java)
                ?.cancel(INCOMING_NOTIFICATION_ID)
        }
    }
}

object ActiveCall {
    val state = mutableStateOf<Call?>(null)
    var current: Call?
        get() = state.value
        set(value) { state.value = value }

    val muted = mutableStateOf(false)
    val audioRoute = mutableStateOf(CallAudioState.ROUTE_EARPIECE)
    val calls = mutableStateListOf<Call>()

    fun setMuted(value: Boolean) {
        val activeService = service
        if (activeService == null) {
            return
        } else {
            runCatching { activeService.setMuted(value) }
                .onFailure { PhoneErrorLog.exception(activeService, "MUTE_FAILED", it) }
        }
    }

    @Volatile
    var service: PhoneInCallService? = null
}

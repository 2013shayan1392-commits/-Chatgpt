package com.shayan.phone

import android.telecom.Call
import android.telecom.InCallService
import androidx.compose.runtime.mutableStateOf

class PhoneInCallService : InCallService() {
    override fun onCreate() {
        super.onCreate()
        ActiveCall.service = this
    }
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        ActiveCall.current = call
    }
    override fun onCallRemoved(call: Call) {
        if (ActiveCall.current == call) ActiveCall.current = null
        super.onCallRemoved(call)
    }
    override fun onDestroy() {
        if (ActiveCall.service === this) ActiveCall.service = null
        super.onDestroy()
    }
}
object ActiveCall {
    val state = mutableStateOf<Call?>(null)
    var current: Call?
        get() = state.value
        set(value) { state.value = value }
    @Volatile var service: PhoneInCallService? = null
}
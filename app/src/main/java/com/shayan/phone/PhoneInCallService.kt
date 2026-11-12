package com.shayan.phone

import android.telecom.Call
import android.telecom.InCallService
import androidx.compose.runtime.mutableStateOf

class PhoneInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        ActiveCall.current = call
    }
    override fun onCallRemoved(call: Call) {
        if (ActiveCall.current == call) ActiveCall.current = null
        super.onCallRemoved(call)
    }
}
object ActiveCall {
    val state = mutableStateOf<Call?>(null)
    var current: Call?
        get() = state.value
        set(value) { state.value = value }
}
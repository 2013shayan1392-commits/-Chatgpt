package com.shayan.phone
import android.telecom.Call
import android.telecom.InCallService
class PhoneInCallService : InCallService() {
 override fun onCallAdded(call: Call) { super.onCallAdded(call); ActiveCall.current = call }
 override fun onCallRemoved(call: Call) { if (ActiveCall.current == call) ActiveCall.current = null; super.onCallRemoved(call) }
}
object ActiveCall { @Volatile var current: Call? = null }

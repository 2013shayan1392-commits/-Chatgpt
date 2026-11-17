package dev.recorderlab.app
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
class PhoneStateReceiver:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent?){
  if(intent?.action!=TelephonyManager.ACTION_PHONE_STATE_CHANGED)return
  val state=intent.getStringExtra(TelephonyManager.EXTRA_STATE)?:return
  val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE); val now=System.currentTimeMillis()
  val old=runCatching{JSONArray(p.getString(KEY_HISTORY,"[]"))}.getOrDefault(JSONArray())
  val arr=JSONArray(); for(i in (old.length()-MAX_EVENTS+1).coerceAtLeast(0) until old.length())arr.put(old.getJSONObject(i))
  arr.put(JSONObject().put("state",state).put("at",now))
  p.edit().putString(KEY_STATE,state).putLong(KEY_CHANGED_AT,now).putString(KEY_HISTORY,arr.toString()).apply()
  val auto=p.getBoolean(KEY_AUTO_RECORD,false)
  if(auto&&state==TelephonyManager.EXTRA_STATE_OFFHOOK){
   val start=Intent(context,RecordingService::class.java).apply{action=RecordingService.ACTION_START;putExtra(RecordingService.EXTRA_MODE,p.getString(KEY_AUTO_MODE,RecordingService.MODE_VOICE_CALL));putExtra(RecordingService.EXTRA_ENGINE,p.getString(KEY_AUTO_ENGINE,RecordingService.ENGINE_MEDIA_RECORDER));putExtra(RecordingService.EXTRA_SAMPLE_RATE,p.getInt(KEY_AUTO_RATE,44100));putExtra(RecordingService.EXTRA_BIT_RATE,p.getInt(KEY_AUTO_BITRATE,128000))}
   try{if(android.os.Build.VERSION.SDK_INT>=26)context.startForegroundService(start) else context.startService(start)}catch(e:Exception){p.edit().putString(RecordingService.KEY_ERROR,"شروع خودکار مسدود شد: ${e.javaClass.simpleName}").apply()}
  }else if(auto&&state==TelephonyManager.EXTRA_STATE_IDLE){runCatching{context.startService(Intent(context,RecordingService::class.java).setAction(RecordingService.ACTION_STOP))}}
 }
 companion object{
  const val PREFS="call_monitor"; const val KEY_STATE="state"; const val KEY_CHANGED_AT="changed_at"; const val KEY_HISTORY="history"
  const val KEY_AUTO_RECORD="auto_record_calls"; const val KEY_AUTO_MODE="auto_record_mode"; const val KEY_AUTO_ENGINE="auto_record_engine"; const val KEY_AUTO_RATE="auto_record_rate"; const val KEY_AUTO_BITRATE="auto_record_bitrate"; const val MAX_EVENTS=50
  fun describe(s:String)=when(s){TelephonyManager.EXTRA_STATE_RINGING->"زنگ‌خوردن";TelephonyManager.EXTRA_STATE_OFFHOOK->"تماس برقرار/پاسخ‌داده‌شده";TelephonyManager.EXTRA_STATE_IDLE->"پایان تماس/آماده";else->"نامشخص"}
  fun formatEvent(s:String,at:Long):String="${SimpleDateFormat("yyyy/MM/dd HH:mm:ss",Locale.getDefault()).format(Date(at))} • ${describe(s)}"
 }
}

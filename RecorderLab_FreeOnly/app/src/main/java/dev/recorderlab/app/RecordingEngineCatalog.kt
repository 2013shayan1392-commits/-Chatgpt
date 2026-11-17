package dev.recorderlab.app
import android.media.MediaRecorder
object RecordingEngineCatalog {
 data class Engine(val id:String,val label:String,val source:Int,val callOnly:Boolean)
 fun publicEngines()=listOf(
  Engine(RecordingService.MODE_MIC,"Microphone",MediaRecorder.AudioSource.MIC,false),
  Engine(RecordingService.MODE_VOICE_CALL,"Voice call",MediaRecorder.AudioSource.VOICE_CALL,true),
  Engine(RecordingService.MODE_UPLINK,"Voice uplink",MediaRecorder.AudioSource.VOICE_UPLINK,true),
  Engine(RecordingService.MODE_DOWNLINK,"Voice downlink",MediaRecorder.AudioSource.VOICE_DOWNLINK,true),
  Engine(RecordingService.MODE_VOICE_COMMUNICATION,"Voice communication",MediaRecorder.AudioSource.VOICE_COMMUNICATION,false))
 fun isCallEngine(id:String)=publicEngines().firstOrNull{it.id==id}?.callOnly==true
}

package dev.recorderlab.app
import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
class RecordingService:Service(){
 private var recorder:MediaRecorder?=null;private var output:File?=null;private var pcm:PcmAudioRecordEngine?=null;private var aac:AudioRecordAacEngine?=null
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{when(intent?.action){ACTION_STOP->{stopRecording();stopSelf(startId)};ACTION_START->startRecording(intent.getIntExtra(EXTRA_SAMPLE_RATE,44100).coerceIn(8000,48000),intent.getIntExtra(EXTRA_BIT_RATE,128000).coerceIn(32000,256000),intent.getStringExtra(EXTRA_MODE)?:MODE_MIC,intent.getStringExtra(EXTRA_ENGINE)?:ENGINE_MEDIA_RECORDER);else->stopSelf(startId)};return START_NOT_STICKY}
 private fun startRecording(rate:Int,bitrate:Int,mode:String,engine:String){
  if(recorder!=null||pcm!=null||aac!=null)return
  val p=getSharedPreferences(PREFS,MODE_PRIVATE)
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){p.edit().putBoolean(KEY_ACTIVE,false).putString(KEY_ERROR,"مجوز میکروفون موجود نیست").apply();stopSelf();return}
  var candidate:MediaRecorder?=null;var file:File?=null
  try{
   p.edit().putString(KEY_MODE,mode).putString(KEY_ENGINE,engine).apply();createChannel()
   val note=notification();if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION_ID,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)else startForeground(NOTIFICATION_ID,note)
   val dir=File(filesDir,"recordings").apply{if(!exists()&&!mkdirs())error("ساخت پوشه ضبط ناموفق بود")}
   val stamp=SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date());val ext=if(engine==ENGINE_AUDIO_RECORD)"wav" else "m4a";var f=File(dir,"REC_$stamp.$ext");var n=1;while(f.exists())f=File(dir,"REC_${stamp}_${n++}.$ext");file=f
   val source=when(mode){MODE_VOICE_CALL->MediaRecorder.AudioSource.VOICE_CALL;MODE_UPLINK->MediaRecorder.AudioSource.VOICE_UPLINK;MODE_DOWNLINK->MediaRecorder.AudioSource.VOICE_DOWNLINK;MODE_VOICE_COMMUNICATION->MediaRecorder.AudioSource.VOICE_COMMUNICATION;else->MediaRecorder.AudioSource.MIC}
   when(engine){
    ENGINE_AUDIO_RECORD->{val e=PcmAudioRecordEngine(f,rate,source);e.start();pcm=e}
    ENGINE_AUDIO_RECORD_AAC->{val e=AudioRecordAacEngine(f,rate,bitrate,source);e.start();aac=e}
    else->{val r=if(Build.VERSION.SDK_INT>=31)MediaRecorder(this)else @Suppress("DEPRECATION") MediaRecorder();candidate=r;r.setAudioSource(source);r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);r.setAudioChannels(1);r.setAudioSamplingRate(rate);r.setAudioEncodingBitRate(bitrate);r.setOutputFile(f.absolutePath);r.prepare();r.start();recorder=r}
   }
   output=f;p.edit().putBoolean(KEY_ACTIVE,true).putLong(KEY_STARTED_AT,System.currentTimeMillis()).putString(KEY_LAST_PATH,f.absolutePath).remove(KEY_ERROR).apply()
  }catch(e:Exception){runCatching{candidate?.reset()};runCatching{candidate?.release()};file?.delete();recorder=null;pcm=null;aac=null;output=null;p.edit().putBoolean(KEY_ACTIVE,false).putString(KEY_ERROR,e.localizedMessage?.take(180)?:"شروع ضبط ناموفق بود").apply();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
 }
 private fun stopRecording(){val r=recorder;val p=pcm;val a=aac;recorder=null;pcm=null;aac=null;var saved=false;try{when{p!=null->{p.stop();saved=true};a!=null->{a.stop();saved=true};r!=null->{r.stop();saved=true}}}catch(_:Exception){}finally{runCatching{r?.reset()};runCatching{r?.release()};if(!saved)output?.delete();output=null};getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean(KEY_ACTIVE,false).putLong(KEY_STARTED_AT,0).apply();stopForeground(STOP_FOREGROUND_REMOVE)}
 private fun createChannel(){if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID,"ضبط صدا",NotificationManager.IMPORTANCE_LOW).apply{description="اعلان آشکار ضبط صدا"})}
 private fun notification():Notification{val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);val stop=PendingIntent.getService(this,1,Intent(this,RecordingService::class.java).setAction(ACTION_STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);val b=if(Build.VERSION.SDK_INT>=26)Notification.Builder(this,CHANNEL_ID)else @Suppress("DEPRECATION") Notification.Builder(this);return b.setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("RecorderLab — ضبط فعال").setContentText("ضبط آشکار فعال است").setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(android.R.drawable.ic_media_pause,"توقف ضبط",stop).build()).build()}
 override fun onDestroy(){if(recorder!=null||pcm!=null||aac!=null)stopRecording();super.onDestroy()}
 companion object{
  const val ACTION_START="dev.recorderlab.app.action.START";const val ACTION_STOP="dev.recorderlab.app.action.STOP";const val EXTRA_SAMPLE_RATE="sample_rate";const val EXTRA_BIT_RATE="bit_rate";const val EXTRA_MODE="recording_mode";const val EXTRA_ENGINE="recording_engine"
  const val ENGINE_MEDIA_RECORDER="media_recorder";const val ENGINE_AUDIO_RECORD="audio_record_pcm";const val ENGINE_AUDIO_RECORD_AAC="audio_record_aac"
  const val MODE_MIC="mic";const val MODE_VOICE_CALL="voice_call";const val MODE_UPLINK="voice_uplink";const val MODE_DOWNLINK="voice_downlink";const val MODE_VOICE_COMMUNICATION="voice_communication"
  const val PREFS="recorder_state";const val KEY_ACTIVE="active";const val KEY_STARTED_AT="started_at";const val KEY_LAST_PATH="last_path";const val KEY_ERROR="last_error";const val KEY_MODE="recording_mode";const val KEY_ENGINE="recording_engine";private const val CHANNEL_ID="recorder_active";private const val NOTIFICATION_ID=4101
 }
}

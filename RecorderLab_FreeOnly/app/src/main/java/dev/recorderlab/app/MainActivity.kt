package dev.recorderlab.app
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import rikka.shizuku.Shizuku
import java.io.File
class MainActivity:Activity(){
 private lateinit var status:TextView;private lateinit var source:Spinner;private lateinit var engine:Spinner;private lateinit var start:Button;private lateinit var stop:Button
 private var player:MediaPlayer?=null
 private val SHIZUKU_REQUEST_CODE=7301
 private lateinit var shizukuStatus:TextView
 private lateinit var shizukuGrant:Button
 private val binderReceived=Shizuku.OnBinderReceivedListener{updateShizuku()}
 private val binderDead=Shizuku.OnBinderDeadListener{updateShizuku()}
 private val permissionResult=Shizuku.OnRequestPermissionResultListener{code,result->
  if(code==SHIZUKU_REQUEST_CODE){Toast.makeText(this,if(result==PackageManager.PERMISSION_GRANTED)"مجوز Shizuku تأیید شد" else "مجوز Shizuku رد شد",Toast.LENGTH_LONG).show();updateShizuku()}
 }
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);buildUi();registerShizuku();refresh()}
 override fun onResume(){super.onResume();updateShizuku()}
 private fun buildUi(){
  val scroll=ScrollView(this);val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,24,24,24)};scroll.addView(root)
  fun label(s:String,size:Float=16f){root.addView(TextView(this).apply{text=s;textSize=size;setPadding(0,10,0,8)})}
  label("RecorderLab",28f);label("ضبط صدا و آزمایش منابع صوتی Android",16f)
  label("اتصال Shizuku",19f)
  shizukuStatus=TextView(this).apply{textSize=14f};root.addView(shizukuStatus)
  shizukuGrant=Button(this).apply{text="درخواست مجوز Shizuku";setOnClickListener{requestShizuku()}};root.addView(shizukuGrant,LinearLayout.LayoutParams(-1,-2))
  root.addView(Button(this).apply{text="بازکردن Shizuku Manager";setOnClickListener{val i=packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api");if(i!=null)startActivity(i) else Toast.makeText(this@MainActivity,"Shizuku Manager نصب نیست",Toast.LENGTH_LONG).show()}},LinearLayout.LayoutParams(-1,-2))
  label("توجه: دسترسی به صدای تماس توسط Android یا سازنده ممکن است مسدود شود. تشخیص وضعیت تماس به معنی ضبط صدای دو طرف نیست.")
  source=Spinner(this);source.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("میکروفون","تماس کامل — VOICE_CALL","صدای ارسالی — VOICE_UPLINK","صدای دریافتی — VOICE_DOWNLINK","ارتباط صوتی — VOICE_COMMUNICATION"));root.addView(source)
  engine=Spinner(this);engine.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("MediaRecorder AAC / M4A","AudioRecord PCM / WAV","AudioRecord + MediaCodec AAC / M4A"));root.addView(engine)
  label("نرخ نمونه‌برداری");val rate=Spinner(this);rate.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("16000","22050","44100"));root.addView(rate)
  label("بیت‌ریت");val bit=Spinner(this);bit.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("64000","96000","128000","192000"));root.addView(bit)
  status=TextView(this).apply{textSize=15f};root.addView(status)
  start=Button(this).apply{text="شروع ضبط";setOnClickListener{if(!hasPermissions()){requestPermissions(requiredPermissions(),100)}else startRecording(rate.selectedItem.toString().toInt(),bit.selectedItem.toString().toInt())}};root.addView(start,LinearLayout.LayoutParams(-1,-2))
  stop=Button(this).apply{text="توقف ضبط";setOnClickListener{sendService(RecordingService.ACTION_STOP);refresh()}};root.addView(stop,LinearLayout.LayoutParams(-1,-2))
  label("فایل‌های ضبط‌شده",18f);val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};root.addView(list)
  val refresh=Button(this).apply{text="به‌روزرسانی فهرست";setOnClickListener{loadFiles(list)}};root.addView(refresh)
  loadFiles(list);setContentView(scroll)
 }
 private fun requiredPermissions():Array<String>{val p= mutableListOf(Manifest.permission.RECORD_AUDIO,Manifest.permission.READ_PHONE_STATE);if(Build.VERSION.SDK_INT>=33)p.add(Manifest.permission.POST_NOTIFICATIONS);return p.toTypedArray()}
 private fun hasPermissions()=requiredPermissions().all{ContextCompat.checkSelfPermission(this,it)==PackageManager.PERMISSION_GRANTED}
 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==100&&hasPermissions())startRecording(44100,128000)else status.text="برای ضبط، مجوز میکروفون لازم است."}
 private fun startRecording(rate:Int,bitrate:Int){
  val modes=listOf(RecordingService.MODE_MIC,RecordingService.MODE_VOICE_CALL,RecordingService.MODE_UPLINK,RecordingService.MODE_DOWNLINK,RecordingService.MODE_VOICE_COMMUNICATION)
  val engines=listOf(RecordingService.ENGINE_MEDIA_RECORDER,RecordingService.ENGINE_AUDIO_RECORD,RecordingService.ENGINE_AUDIO_RECORD_AAC)
  val i=Intent(this,RecordingService::class.java).apply{action=RecordingService.ACTION_START;putExtra(RecordingService.EXTRA_MODE,modes[source.selectedItemPosition]);putExtra(RecordingService.EXTRA_ENGINE,engines[engine.selectedItemPosition]);putExtra(RecordingService.EXTRA_SAMPLE_RATE,rate);putExtra(RecordingService.EXTRA_BIT_RATE,bitrate)}
  try{if(Build.VERSION.SDK_INT>=26)ContextCompat.startForegroundService(this,i)else startService(i);status.text="درخواست شروع ضبط ارسال شد."}catch(e:Exception){status.text="خطا: ${e.localizedMessage}"}
 }
 private fun sendService(action:String){startService(Intent(this,RecordingService::class.java).setAction(action))}
 private fun refresh(){val p=getSharedPreferences(RecordingService.PREFS,MODE_PRIVATE);status.text=if(p.getBoolean(RecordingService.KEY_ACTIVE,false))"وضعیت: ضبط فعال" else "وضعیت: آماده\n"+(p.getString(RecordingService.KEY_ERROR,"")?:"")}
 private fun loadFiles(container:LinearLayout){
  container.removeAllViews();val dir=File(filesDir,"recordings");val files=dir.listFiles()?.filter{it.isFile}?.sortedByDescending{it.lastModified()}.orEmpty()
  if(files.isEmpty()){container.addView(TextView(this).apply{text="هنوز فایلی ذخیره نشده است"});return}
  files.forEach{f->val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
   val play=Button(this).apply{text="پخش: ${f.name}";setOnClickListener{playFile(f)}}
   val share=Button(this).apply{text="اشتراک";setOnClickListener{shareFile(f)}}
   row.addView(play,LinearLayout.LayoutParams(0,-2,2f));row.addView(share,LinearLayout.LayoutParams(0,-2,1f));container.addView(row)
  }
 }
 private fun playFile(file:File){runCatching{player?.release();player=MediaPlayer().apply{setDataSource(file.absolutePath);prepare();start()}}.onFailure{status.text="پخش ناموفق: ${it.localizedMessage}"}}
 private fun shareFile(file:File){runCatching{val uri=FileProvider.getUriForFile(this,"${packageName}.fileprovider",file);startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(if(file.extension=="wav")"audio/wav" else "audio/mp4").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"اشتراک فایل ضبط‌شده"))}.onFailure{status.text="اشتراک ناموفق: ${it.localizedMessage}"}}
 private fun registerShizuku(){try{Shizuku.addBinderReceivedListenerSticky(binderReceived);Shizuku.addBinderDeadListener(binderDead);Shizuku.addRequestPermissionResultListener(permissionResult)}catch(_:Throwable){};updateShizuku()}
 private fun updateShizuku(){if(!::shizukuStatus.isInitialized)return;val connected=try{Shizuku.pingBinder()}catch(_:Throwable){false};val granted=connected&&try{Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED}catch(_:Throwable){false};shizukuStatus.text=when{!connected->"سرویس Shizuku متصل نیست؛ ابتدا آن را در برنامه Shizuku راه‌اندازی کنید.";granted->"متصل و مجاز ✓";else->"سرویس متصل است؛ مجوز برنامه هنوز صادر نشده است."};shizukuGrant.isEnabled=connected&&!granted;shizukuGrant.text=if(granted)"مجوز صادر شده" else "درخواست مجوز Shizuku"}
 private fun requestShizuku(){try{if(!Shizuku.pingBinder()){updateShizuku();return};if(Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED){updateShizuku()}else Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)}catch(e:Throwable){Toast.makeText(this,"خطای Shizuku: "+e.message,Toast.LENGTH_LONG).show()}}
 override fun onDestroy(){try{Shizuku.removeBinderReceivedListener(binderReceived);Shizuku.removeBinderDeadListener(binderDead);Shizuku.removeRequestPermissionResultListener(permissionResult)}catch(_:Throwable){};player?.release();player=null;super.onDestroy()}
}

package dev.recorderlab.app
import android.content.Context
import android.os.Build
object CallRecordingCapability {
 data class Result(val phoneStateDetection:Boolean,val callAudioCaptureGuaranteed:Boolean,val explanation:String)
 fun assess(context:Context):Result {
  val granted=context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE)==android.content.pm.PackageManager.PERMISSION_GRANTED
  return Result(granted,false,if(!granted) "برای تشخیص وضعیت تماس، مجوز تلفن لازم است؛ این مجوز صدای مکالمه را ضبط نمی‌کند." else "منابع صدای تماس ممکن است توسط Android یا سازنده مسدود شوند؛ ضبط دوطرفه تضمین نمی‌شود.")
 }
 fun platformNote()=if(Build.VERSION.SDK_INT>=29) "Android 10 و جدیدتر: دسترسی ضبط تماس محدود است و باید روی هر دستگاه آزمایش شود." else "پشتیبانی وابسته به نسخه و سازنده است."
}

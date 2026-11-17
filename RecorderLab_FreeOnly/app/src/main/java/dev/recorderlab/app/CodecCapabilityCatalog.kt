package dev.recorderlab.app
import android.media.MediaCodecList
import android.os.Build
object CodecCapabilityCatalog {
 data class Codec(val name:String,val mime:String,val encoder:Boolean,val sampleRates:List<Int>,val maxChannels:Int)
 fun discover():List<Codec> {
  if(Build.VERSION.SDK_INT<21)return emptyList()
  return runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.flatMap { info ->
   info.supportedTypes.mapNotNull { mime ->
    val audio=runCatching { info.getCapabilitiesForType(mime).audioCapabilities }.getOrNull() ?: return@mapNotNull null
    Codec(info.name,mime,info.isEncoder,audio.supportedSampleRates.sorted(),audio.maxInputChannelCount)
   }
  }.sortedWith(compareBy<Codec>({it.mime},{!it.encoder},{it.name})) }.getOrDefault(emptyList())
 }
 fun encoders()=discover().filter{it.encoder}
 fun supportsEncoder(mime:String,sampleRate:Int,channels:Int=1)=encoders().any{
  it.mime.equals(mime,true)&&(it.sampleRates.isEmpty()||sampleRate in it.sampleRates)&&it.maxChannels>=channels
 }
}

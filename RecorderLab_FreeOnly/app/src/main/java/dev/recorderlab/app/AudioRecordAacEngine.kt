package dev.recorderlab.app
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
class AudioRecordAacEngine(private val output:File,private val sampleRate:Int,private val bitRate:Int,private val audioSource:Int){
 private var audio:AudioRecord?=null;private var codec:MediaCodec?=null;private var muxer:MediaMuxer?=null;private var worker:Thread?=null
 private val running=AtomicBoolean(false);@Volatile private var failure:Throwable?=null
 @Synchronized fun start(){
  check(worker==null){"AAC engine already started"};check(CodecCapabilityCatalog.supportsEncoder("audio/mp4a-latm",sampleRate)){"AAC encoder unavailable"}
  val min=AudioRecord.getMinBufferSize(sampleRate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);check(min>0){"Unsupported AudioRecord config"}
  val input=AudioRecord(audioSource,sampleRate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,(min*2).coerceAtLeast(sampleRate/2))
  check(input.state==AudioRecord.STATE_INITIALIZED){input.release();"AudioRecord initialization failed"}
  var enc:MediaCodec?=null;var mux:MediaMuxer?=null
  try{
   val fmt=MediaFormat.createAudioFormat("audio/mp4a-latm",sampleRate,1).apply{setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);setInteger(MediaFormat.KEY_BIT_RATE,bitRate);setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,min)}
   val e=MediaCodec.createEncoderByType("audio/mp4a-latm");enc=e;e.configure(fmt,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);e.start()
   val m=MediaMuxer(output.absolutePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);mux=m;input.startRecording();check(input.recordingState==AudioRecord.RECORDSTATE_RECORDING)
   audio=input;codec=e;muxer=m;running.set(true);worker=Thread({encode(input,e,m,min)},"RecorderLab-AAC").apply{start()}
  }catch(t:Throwable){runCatching{input.stop()};input.release();runCatching{enc?.stop()};runCatching{enc?.release()};runCatching{mux?.release()};output.delete();throw t}
 }
 private fun encode(input:AudioRecord,e:MediaCodec,m:MediaMuxer,min:Int){
  val info=MediaCodec.BufferInfo();var started=false;var track=-1;var ended=false;var samples=0L;val pcm=ByteArray(min.coerceAtLeast(4096))
  try{
   while(running.get()||!ended){
    if(running.get()){
     val n=input.read(pcm,0,pcm.size,AudioRecord.READ_BLOCKING)
     if(n>0){var off=0;while(off<n){val ix=e.dequeueInputBuffer(10000);if(ix<0)continue;val b=e.getInputBuffer(ix)?:error("AAC input unavailable");b.clear();val count=minOf(b.remaining(),n-off);b.put(pcm,off,count);e.queueInputBuffer(ix,0,count,samples*1000000L/sampleRate,0);samples+=count/2;off+=count}}
     else if(n<0)error("AudioRecord read failed: $n")
    }else if(!ended){val ix=e.dequeueInputBuffer(10000);if(ix>=0){e.queueInputBuffer(ix,0,0,samples*1000000L/sampleRate,MediaCodec.BUFFER_FLAG_END_OF_STREAM);ended=true}}
    var ix=e.dequeueOutputBuffer(info,10000)
    while(ix>=0){val b=e.getOutputBuffer(ix);if(info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG!=0)info.size=0
     if(info.size>0&&started&&b!=null){b.position(info.offset);b.limit(info.offset+info.size);m.writeSampleData(track,b,info)}
     e.releaseOutputBuffer(ix,false);if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0)break;ix=e.dequeueOutputBuffer(info,0)}
    if(ix==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){check(!started);track=m.addTrack(e.outputFormat);m.start();started=true}
    if(ended&&ix==MediaCodec.INFO_TRY_AGAIN_LATER)break
   }
   check(started){"AAC produced no output format"}
  }catch(t:Throwable){failure=t}finally{runCatching{if(started)m.stop()}}
 }
 @Synchronized fun stop(){val a=audio?:return;running.set(false);runCatching{a.stop()};runCatching{worker?.join(5000)};audio=null;worker=null;a.release();runCatching{codec?.stop()};runCatching{codec?.release()};codec=null;runCatching{muxer?.release()};muxer=null;failure?.let{output.delete();throw IllegalStateException("AAC recording failed",it)};check(output.exists()&&output.length()>64){"AAC output empty"}}
}

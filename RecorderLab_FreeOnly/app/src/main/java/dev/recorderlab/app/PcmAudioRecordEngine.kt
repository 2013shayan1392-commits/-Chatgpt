package dev.recorderlab.app
import android.media.AudioFormat
import android.media.AudioRecord
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
class PcmAudioRecordEngine(private val output:File,private val sampleRate:Int,private val audioSource:Int){
 private var recorder:AudioRecord?=null; private var worker:Thread?=null; private val running=AtomicBoolean(false); @Volatile private var failure:Throwable?=null
 @Synchronized fun start(){
  check(recorder==null)
  val min=AudioRecord.getMinBufferSize(sampleRate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT); check(min>0){"Unsupported AudioRecord buffer: $min"}
  val audio=AudioRecord(audioSource,sampleRate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,(min*2).coerceAtLeast(sampleRate/2))
  check(audio.state==AudioRecord.STATE_INITIALIZED){audio.release();"AudioRecord initialization failed"}
  recorder=audio
  try{RandomAccessFile(output,"rw").use{it.setLength(0);header(it,0)};audio.startRecording();check(audio.recordingState==AudioRecord.RECORDSTATE_RECORDING);running.set(true);worker=Thread({pump(audio)},"RecorderLab-PCM").apply{start()}}
  catch(t:Throwable){running.set(false);runCatching{audio.stop()};audio.release();recorder=null;output.delete();throw t}
 }
 private fun pump(a:AudioRecord){val buf=ShortArray(4096);var bytes=0L;try{RandomAccessFile(output,"rw").use{f->f.seek(44);while(running.get()){val n=a.read(buf,0,buf.size,AudioRecord.READ_BLOCKING);if(n>0){for(i in 0 until n){val v=buf[i].toInt();f.write(v and 255);f.write((v shr 8) and 255)};bytes+=n*2L}else if(n<0)error("AudioRecord read failed: $n")};f.seek(0);header(f,bytes)}}catch(t:Throwable){failure=t}}
 @Synchronized fun stop(){running.set(false);val a=recorder;recorder=null;runCatching{a?.stop()};runCatching{worker?.join(1500)};worker=null;a?.release();failure?.let{throw IllegalStateException("PCM capture failed",it)}}
 private fun header(f:RandomAccessFile,n:Long){f.writeBytes("RIFF");i32(f,(36+n).toInt());f.writeBytes("WAVEfmt ");i32(f,16);i16(f,1);i16(f,1);i32(f,sampleRate);i32(f,sampleRate*2);i16(f,2);i16(f,16);f.writeBytes("data");i32(f,n.toInt())}
 private fun i32(f:RandomAccessFile,v:Int){f.write(v and 255);f.write(v ushr 8 and 255);f.write(v ushr 16 and 255);f.write(v ushr 24 and 255)}
 private fun i16(f:RandomAccessFile,v:Int){f.write(v and 255);f.write(v ushr 8 and 255)}
}

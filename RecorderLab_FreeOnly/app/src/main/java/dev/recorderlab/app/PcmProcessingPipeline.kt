package dev.recorderlab.app
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
class PcmBlockQueue(capacity:Int=32) {
 private val queue=ArrayBlockingQueue<ShortArray>(capacity.coerceIn(2,256))
 @Volatile var droppedBlocks:Long=0; private set
 fun offer(block:ShortArray):Boolean { val copy=block.copyOf(); if(queue.offer(copy))return true; queue.poll(); droppedBlocks++; return queue.offer(copy) }
 @Throws(InterruptedException::class) fun poll(timeoutMs:Long=250):ShortArray?=queue.poll(timeoutMs,TimeUnit.MILLISECONDS)
 fun clear()=queue.clear()
 fun size()=queue.size
}
object PcmResampler {
 fun resample(input:ShortArray,inputRate:Int,outputRate:Int):ShortArray {
  require(inputRate in 8000..192000&&outputRate in 8000..192000)
  if(input.isEmpty()||inputRate==outputRate)return input.copyOf()
  val n=(input.size.toDouble()*outputRate/inputRate).toInt().coerceAtLeast(1)
  return ShortArray(n){i-> val p=i.toDouble()*inputRate/outputRate; val l=p.toInt().coerceIn(0,input.lastIndex); val r=(l+1).coerceAtMost(input.lastIndex); (input[l]*(1f-(p-l).toFloat())+input[r]*(p-l).toFloat()).toInt().coerceIn(-32768,32767).toShort() }
 }
 fun applyGain(input:ShortArray,gain:Float):ShortArray { require(gain.isFinite()&&gain in 0f..8f); return ShortArray(input.size){(input[it]*gain).toInt().coerceIn(-32768,32767).toShort()} }
}

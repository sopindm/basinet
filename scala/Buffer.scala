package basinet

import java.nio.{BufferUnderflowException, BufferOverflowException}
import java.nio.channels.{ClosedChannelException}
import scala.math.min
import scala.annotation.tailrec
import scala.language.existentials

trait Buffer extends Channel {
  def size: Int
  def drop(n: Int): Unit
  def expand(n: Int): Unit
}

trait BufferLike extends Buffer {
  private[this] var tail = 0

  def begin: Int
  def end: Int
  override def size = end - begin + tail
  def capacity: Int

  def reset(begin: Int, end: Int): Unit

  def compactionThreshold: Int

  def compact(begin: Int, end: Int) = reset(begin, end)

  override def drop(n: Int) {
    if(begin + n >= capacity) {
      val newSize = size - n
      val newBegin = begin + n - capacity + compactionThreshold

      reset(newBegin, newBegin + newSize)
      tail = 0
    }
    else reset(begin + n, end)

    if(begin >= capacity - compactionThreshold && end == capacity) {
      val newBegin = compactionThreshold - (capacity - begin)
      compact(newBegin, newBegin + size)
      tail = 0
    }
  }

  override def expand(n: Int) {
    val inc = min(capacity - end, n)
    reset(begin, end + inc)
    tail += n - inc
  }

  protected def indexToPosition(index: Int) = {
    if(index < 0 || index >= size) throw new IllegalArgumentException
      (begin + index) % capacity
  }

  def copy(to: Int, from: Int, size: Int)
}

trait BufferSource[SR <: BufferSource[SR, T], T]
    extends SourceLike[SR, T] with Buffer {
  def get(index: Int): T

  override def poppable = super.poppable && size > 0
  override def _pop = { val result = get(0); drop(1); result }

  override def pop = if(poppable) super.pop
  else
    throw new BufferUnderflowException
}

trait BufferSourceLike[SR <: BufferSourceLike[SR, SN, T], SN <: BufferSinkLike[SR, SN, T], T]
    extends BufferSource[SR, T] with BufferLike with ChannelLike {
  def sink: BufferSinkLike[SR, SN, T]

  def absoluteGet(index: Int): T
  override def get(index: Int) = { requireOpen; absoluteGet(indexToPosition(index)) }

  override def _close = { super._close; if(sink.isOpen) sink.close }

  override val onPoppable = new evil_ant.Event(false)

  def _drop(n: Int) = { super.drop(n); if(!poppable && !sink.isOpen) close }
  def _expand(n: Int) { 
    val becamePoppable = size == 0 && n > 0 && isOpen
    super.expand(n)
    if(becamePoppable) onPoppable.emit(this)
  }

  override def drop(n: Int) = { _drop(n); sink._expand(n) }
  override def expand(n: Int) = { _expand(n); sink._drop(n) }

  override def compact(begin: Int, end: Int) {
    copy(begin, capacity - compactionThreshold + begin, min(end - begin, compactionThreshold - begin))
    super.compact(begin, end)
  }
}

trait BufferSink[SN <: BufferSink[SN, T], T]
    extends SinkLike[SN, T] with Buffer {
  def set(index: Int, value: T): Unit

  override def pushable = super.pushable && size > 0
  override def _push(value: T) { set(0, value); drop(1) }

  override def push(value: T) = if(pushable) super.push(value)
  else
    throw new BufferOverflowException
}

trait BufferSinkLike[SR <: BufferSourceLike[SR, SN, T], SN <: BufferSinkLike[SR, SN, T], T]
    extends BufferSink[SN, T] with BufferLike with ChannelLike {
  def source: BufferSourceLike[SR, SN, T]

  def absoluteSet(index: Int, value: T): Unit
  override def set(index: Int, value: T) { requireOpen; absoluteSet(indexToPosition(index), value) }

  override def _close = { super._close; if(!source.poppable && source.isOpen) source.close }

  override val onPushable = new evil_ant.Event(false)

  def _drop(n: Int) {
    val begin = this.begin

    super.drop(n)

    if(begin < compactionThreshold)
      copy(capacity - compactionThreshold + begin, begin, min(n, compactionThreshold - begin))
  }

  def _expand(n: Int) { 
    val becamePushable = size == 0 && n > 0 && isOpen
    super.expand(n)
    if(becamePushable) onPushable.emit(this)
  }

  override def drop(n: Int) { _drop(n); source._expand(n) }
  override def expand(n: Int) = { _expand(n); source._drop(n) }
}

package any {
  class Buffer[T](buffer: Array[T], var begin: Int, var end: Int, override val compactionThreshold: Int)
      extends basinet.BufferLike with ChannelLike {
    override def capacity = buffer.size

    override def copy(to: Int, from: Int, size: Int) {
      for(i <- 0 until size) buffer(to + i) = buffer(from + i)
    }

    override def reset(newBegin: Int, newEnd: Int) { begin = newBegin; end = newEnd }
  }

  class BufferSource[T](buffer: Array[T], size: Int, compactionThreshold: Int,
    pipe: basinet.Pipe[BufferSource[T], BufferSink[T], T, T])
      extends Buffer[T](buffer, 0, size, compactionThreshold)
      with basinet.BufferSourceLike[BufferSource[T], BufferSink[T], T] {
    override def source = this
    override def sink = pipe.sink
    override def absoluteGet(index: Int) = buffer(index)
  }

  class BufferSink[T](buffer: Array[T], size: Int, compactionThreshold: Int,
                      pipe: basinet.Pipe[BufferSource[T], BufferSink[T], T, T])
      extends Buffer[T](buffer, size, buffer.size - compactionThreshold, compactionThreshold)
      with basinet.BufferSinkLike[BufferSource[T], BufferSink[T], T] {
    override def sink = this
    override def source = pipe.source
    override def absoluteSet(index: Int, value: T) { buffer(index) = value }
  }

  class BufferPipe[T](buffer: Array[T], size: Int, compactionThreshold: Int)
      extends basinet.PipeLike[BufferSource[T], BufferSink[T], T, T] {
    override val source = new BufferSource[T](buffer, size, compactionThreshold, this)
    override val sink = new BufferSink[T](buffer, size, compactionThreshold, this)
  }

  class BufferWriter[T, U <: basinet.Source[U, T]] extends basinet.Wire[U, BufferSink[T]] {
    @tailrec
    private[this] def _write(from: U, to: BufferSink[T]): basinet.Result = {
      if(!to.pushable) return Result.OVERFLOW
        
      from.tryPop match {
        case Some(result) => { to.push(result); _write(from, to) }
        case None => Result.UNDERFLOW
      }
    }

    override def _convert(from: U, to: BufferSink[T]) = _write(from, to)
  }

  class BufferReader[T, U <: basinet.Sink[U, T]] extends basinet.Wire[BufferSource[T], U] {
    @tailrec
    private[this] def _read(from: BufferSource[T], to: U): basinet.Result = {
      if(!from.poppable) Result.UNDERFLOW
      else if(to.tryPush(from.get(0))) { from.drop(1); _read(from, to) }
      else Result.OVERFLOW
    }

    override def _convert(from: BufferSource[T], to: U) = _read(from, to)
  }
}

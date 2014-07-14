package basinet

import java.nio.{BufferUnderflowException, BufferOverflowException}
import java.nio.channels.{ClosedChannelException}

trait Buffer extends Channel {
  def size: Int
  def drop(n: Int): Unit
  def expand(n: Int): Unit

  def requireOpen = if(!isOpen) throw new ClosedChannelException
}

trait BufferLike extends Buffer {
  private[this] var tail = 0

  def begin: Int
  def end: Int
  override def size = end - begin + tail
  def capacity: Int

  def reset(begin: Int, end: Int): Unit

  def compactionThreshold: Int

  def compact {
    val newBegin = compactionThreshold - (capacity - begin)
    reset(newBegin, newBegin + size)
  }

  override def drop(n: Int) {
    requireOpen
    if(begin + n >= capacity) {
      val newSize = size - n
      val newBegin = begin + n - capacity + compactionThreshold

      reset(newBegin, newBegin + newSize)
      tail = 0
    }
    else reset(begin + n, end)

    if(begin >= capacity - compactionThreshold) compact
  }

  override def expand(n: Int) {
    requireOpen
    val inc = scala.math.min(capacity - end, n)
    reset(begin, end + inc)
    tail += n - inc
  }

  protected def indexToPosition(index: Int) = {
    if(index < 0 || index >= size) throw new IllegalArgumentException
      (begin + index) % capacity
  }
}

trait BufferSource[SR <: BufferSource[SR, T], T]
    extends Source[SR, T] with Buffer {
  def get(index: Int): T

  override def poppable = isOpen && size > 0
  override def tryPop =  if(poppable) {
    val result = get(0); drop(1); Some(result)
  }
  else None

  override def pop = if(poppable) super.pop
  else
    throw new BufferUnderflowException
}

trait BufferSourceLike[SR <: BufferSourceLike[SR, SN, T], SN <: BufferSinkLike[SR, SN, T], T]
    extends BufferSource[SR, T] with BufferLike with ChannelLike {
  def sink: BufferSinkLike[SR, SN, T]

  def absoluteGet(index: Int): T
  override def get(index: Int) = { requireOpen; absoluteGet(indexToPosition(index)) }

  override def close = { super.close; sink.close }

  def _drop(n: Int) = super.drop(n)
  def _expand(n: Int) = super.expand(n)

  override def drop(n: Int) = { _drop(n); sink._expand(n) }
  override def expand(n: Int) = { _expand(n); sink._drop(n) }
}

trait BufferSink[SN <: BufferSink[SN, T], T]
    extends Sink[SN, T] with Buffer {
  def set(index: Int, value: T): Unit

  override def pushable = isOpen && size > 0
  override def tryPush(value: T) = if(pushable) {
    set(0, value); drop(1); true
  } else false

  override def push(value: T) = if(pushable) super.push(value)
  else
    throw new BufferOverflowException
}

trait BufferSinkLike[SR <: BufferSourceLike[SR, SN, T], SN <: BufferSinkLike[SR, SN, T], T]
    extends BufferSink[SN, T] with BufferLike with ChannelLike {
  def source: BufferSourceLike[SR, SN, T]

  def absoluteSet(index: Int, value: T): Unit
  override def set(index: Int, value: T) { requireOpen; absoluteSet(indexToPosition(index), value) }

  def _drop(n: Int) = super.drop(n)
  def _expand(n: Int) = super.expand(n)

  override def drop(n: Int) = { _drop(n); source._expand(n) }
  override def expand(n: Int) = { _expand(n); source._drop(n) }
}


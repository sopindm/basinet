package basinet

import java.nio.{BufferUnderflowException, BufferOverflowException}
import java.nio.channels.{ClosedChannelException}

trait Buffer extends Channel {
  def size: Int
  def drop(n: Int): Unit
  def expand(n: Int): Unit

  def requireOpen = if(!isOpen) throw new ClosedChannelException
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

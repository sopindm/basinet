package basinet

import java.nio.{BufferUnderflowException, BufferOverflowException}

trait Buffered {
  def size: Int
  def drop(n: Int): Unit
  def expand(n: Int): Unit
}

trait BufferedSource[T] extends Source[T] with Buffered {
  def get(index: Int): T

  override def poppable = size > 0
  override def pop = if(poppable) super.pop else throw new BufferUnderflowException
}

trait BufferedSink[T] extends Buffered with Sink[T] {
  def set(index: Int, value: T): Unit

  override def pushable = size > 0
  override def push(value: T) = if(pushable) super.push(value) else throw new BufferOverflowException
}

abstract class Buffer[T] extends Pipe[T] with BufferedSource[T] with BufferedSink[T] {
  override def source: BufferedSource[T]
  override def sink: BufferedSink[T]

  override def pushable = sink.pushable

  override def push(value: T) { sink.push(value); source.expand(1) }
  override def pushIn(value: T, milliseconds: Int) =
    if(sink.pushIn(value, milliseconds)) { source.expand(1); true } else false 

  override def tryPush(value: T) =
    if(sink.tryPush(value)) { source.expand(1); true } else false

  override def pop = { val result = source.pop; sink.expand(1); result }

  override def popIn(milliseconds: Int) = source.popIn(milliseconds) match {
    case result@Some(_) => { sink.expand(1); result }
    case None => None
  }

  override def tryPop = source.tryPop match {
    case result@Some(_) => { sink.expand(1); result }
    case None => None
  }

  override def drop(n: Int) = { source.drop(n); sink.expand(n) }
  override def expand(n: Int) = { source.expand(n); sink.drop(n) }
  override def size = source.size

  override def get(index: Int) = source.get(index)
  override def set(index: Int, value: T) = sink.set(index, value)
}

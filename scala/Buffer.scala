package basinet

import java.nio.{BufferUnderflowException, BufferOverflowException}

trait Buffered {
  def size: Int
  def drop(n: Int): Unit
  def expand(n: Int): Unit
}

trait BufferedSource[T] extends Buffered with SourceLike[T] {
  override def poppable = size > 0
  override def pop = if(poppable) super.pop else throw new BufferUnderflowException
}

trait BufferedSink[T] extends Buffered with SinkLike[T] {
  override def pushable = size > 0
  override def push(value: T) = if(pushable) super.push(value) else throw new BufferOverflowException
}

abstract class Buffer[T] extends Source[T] with Sink[T] {
  def source: BufferedSource[T]
  def sink: BufferedSink[T]

  override def push(value: T) { sink.push(value); source.expand(1) }
  override def pushIn(value: T, milliseconds: Int) =
    if(sink.pushIn(value, milliseconds)) { source.expand(1); true } else false 

  override def tryPush(value: T) =
    if(sink.tryPush(value)) { source.expand(1); true } else false

  override def pop = { val result = source.pop; /*sink.expand(1);*/ result }

  override def popIn(milliseconds: Int) = source.popIn(milliseconds) match {
    case result@Some(_) => { /*sink.expand(1);*/ result }
    case None => None
  }

  override def tryPop = source.tryPop match {
    case result@Some(_) => { /*sink.expand(1);*/ result }
    case None => None
  }
}

class NIOBuffered(buffer: java.nio.Buffer) extends Buffered {
  override def size = buffer.limit - buffer.position

  override def drop(n: Int) = buffer.position(buffer.position + n)
  override def expand(n: Int) = buffer.limit(buffer.limit + n)
}

class ByteBuffer(buffer: java.nio.ByteBuffer) extends Buffer[Byte] {
  self: ByteBuffer =>

  class ByteBuffered(val buffer: java.nio.ByteBuffer) extends NIOBuffered(buffer)

  override val source = new ByteBuffered(buffer.duplicate) with BufferedSource[Byte] {
    buffer.position(0)
    override def tryPop = if(poppable) Some(buffer.get) else None
  }

  override val sink = new ByteBuffered(buffer.duplicate) with BufferedSink[Byte] {
    buffer.position(self.buffer.limit).limit(self.buffer.capacity)
    override def tryPush(value: Byte) = if(pushable) { buffer.put(value); true } else false
  }
}

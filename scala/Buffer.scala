package basinet

import java.nio.{BufferUnderflowException, BufferOverflowException}

trait Buffered {
  def size: Int
  def drop(n: Int): Unit
  def expand(n: Int): Unit
}

trait RandomAccessSource[T] extends Source[T] {
  def get(index: Int): T
}

trait RandomAccessSink[T] extends Sink[T] {
  def set(index: Int, value: T): Unit
}

trait BufferedSource[T] extends Buffered
    with SourceLike[T] with RandomAccessSource[T] {
  override def poppable = size > 0
  override def pop = if(poppable) super.pop else throw new BufferUnderflowException
}

trait BufferedSink[T] extends Buffered
    with SinkLike[T] with RandomAccessSink[T] {
  override def pushable = size > 0
  override def push(value: T) = if(pushable) super.push(value) else throw new BufferOverflowException

}

abstract class Buffer[T] extends Pipe[T]
    with RandomAccessSource[T] with RandomAccessSink[T] with Buffered {
  override def source: BufferedSource[T]
  override def sink: BufferedSink[T]

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

class NIOBuffered(buffer: java.nio.Buffer) extends Buffered {
  private[this] var tail = 0
  override def size = buffer.limit - buffer.position + tail

  override def drop(n: Int) = if(buffer.position + n >= buffer.capacity) {
    val newSize = size - n
    buffer.position(buffer.position + n - buffer.capacity)
    buffer.limit(buffer.position + newSize)
    tail = 0
  }
  else buffer.position(buffer.position + n)

  override def expand(n: Int) = {
    val inc = scala.math.min(buffer.capacity - buffer.limit, n)
    buffer.limit(buffer.limit + inc)
    tail += n - inc
  }

  def compact = if(buffer.position == buffer.limit) drop(0)

  private[this] def requireValidIndex(index: Int) {

  }

  protected def indexToPosition(index: Int) = {
    if(index < 0 || index >= size) throw new IllegalArgumentException
    (buffer.position + index) % buffer.capacity
  }
}

class ByteBuffer(buffer: java.nio.ByteBuffer) extends Buffer[Byte] {
  self: ByteBuffer =>

  class ByteBuffered(val buffer: java.nio.ByteBuffer) extends NIOBuffered(buffer)

  override val source = new ByteBuffered(buffer.duplicate) with BufferedSource[Byte] {
    buffer.position(0)
    override def tryPop = if(poppable) {val value = Some(buffer.get); compact; value } else None

    override def get(index: Int) = buffer.get(indexToPosition(index))
  }

  override val sink = new ByteBuffered(buffer.duplicate)
      with BufferedSink[Byte] {
    buffer.position(self.buffer.limit).limit(self.buffer.capacity)
    override def tryPush(value: Byte) = if(pushable) {
      buffer.put(value); compact; true }
    else false

    override def set(index: Int, value: Byte) {
      buffer.put(indexToPosition(index), value)
    }
  }
}

object ByteBuffer {
  def apply(n: Int) = {
    val buffer = java.nio.ByteBuffer.allocate(n)
    buffer.limit(0)
    new ByteBuffer(buffer) 
  }
}

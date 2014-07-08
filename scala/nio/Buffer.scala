package basinet.nio

class Buffered(buffer: java.nio.Buffer) extends basinet.Buffered {
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

class ByteBuffer(buffer: java.nio.ByteBuffer) extends basinet.Buffer[Byte] {
  self: ByteBuffer =>

  class ByteBuffered(val buffer: java.nio.ByteBuffer) extends Buffered(buffer)

  override val source = new ByteBuffered(buffer.duplicate)
      with basinet.BufferedSource[Byte] {
    buffer.position(0)
    override def tryPop = if(poppable) {val value = Some(buffer.get); compact; value } else None

    override def get(index: Int) = buffer.get(indexToPosition(index))
  }

  override val sink = new ByteBuffered(buffer.duplicate)
      with basinet.BufferedSink[Byte] {
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

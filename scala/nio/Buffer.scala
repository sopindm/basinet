package basinet.nio

class Buffer(buffer: java.nio.Buffer) extends basinet.Buffer
    with basinet.ChannelLike {
  private[this] var tail = 0
  override def size = buffer.limit - buffer.position + tail

  private[nio]
  def _drop(n: Int) = {
    requireOpen
    if(buffer.position + n >= buffer.capacity) {
      val newSize = size - n
      buffer.position(buffer.position + n - buffer.capacity)
      buffer.limit(buffer.position + newSize)
      tail = 0
    }
    else buffer.position(buffer.position + n)
  }

  private[nio]
  def _expand(n: Int) = {
    requireOpen
    val inc = scala.math.min(buffer.capacity - buffer.limit, n)
    buffer.limit(buffer.limit + inc)
    tail += n - inc
  }

  override def drop(n: Int) = _drop(n)
  override def expand(n: Int) = _expand(n)

  def compact = if(buffer.position == buffer.limit) drop(0)

  protected def indexToPosition(index: Int) = {
    if(index < 0 || index >= size) throw new IllegalArgumentException
    (buffer.position + index) % buffer.capacity
  }
}

package bytebuffer {
  class Buffer(val buffer: java.nio.ByteBuffer)
      extends basinet.nio.Buffer(buffer) {
  }

  class Source(buffer: java.nio.ByteBuffer)
      extends Buffer(buffer) with basinet.BufferSource[Source, Byte] {
    override def source = this
    override def get(index: Int) = {
      requireOpen; buffer.get(indexToPosition(index))
    }
  }

  class Sink(buffer: java.nio.ByteBuffer)
      extends Buffer(buffer) with basinet.BufferSink[Sink, Byte] {
    buffer.position(buffer.limit).limit(buffer.capacity)
    compact

    override def sink = this
    override def set(index: Int, value: Byte) {
      requireOpen; buffer.put(indexToPosition(index), value)
    }
  }
}

class ByteBuffer(buffer: java.nio.ByteBuffer)
    extends basinet.Pipe[bytebuffer.Source, bytebuffer.Sink, Byte]
 {
   self =>

   override def close { source.close; sink.close }
   override def isOpen = source.isOpen || sink.isOpen

   override val source: bytebuffer.Source =
     new bytebuffer.Source(buffer.duplicate) {
       override def close { super.close ; sink.close }

       override def drop(n: Int) = { _drop(n); sink._expand(n) }
       override def expand(n: Int) = { _expand(n); sink._drop(n) }
     }

   override val sink: bytebuffer.Sink =
     new bytebuffer.Sink(buffer.duplicate) {
       override def drop(n: Int) = { _drop(n); source._expand(n) }
       override def expand(n: Int) = { _expand(n); source._drop(n) }
    }
}

object ByteBuffer {
  def apply(n: Int) = {
    val buffer = java.nio.ByteBuffer.allocate(n)
    buffer.limit(0)
    new ByteBuffer(buffer)
  }
}

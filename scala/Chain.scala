package basinet

object Chain {
  def apply[T](channel: SourceChannel[T], buffer: Buffer[T]) = new BufferedSource[T] with Channel {
    override def source = buffer

    override def isOpen = channel.isOpen
    override def close = channel.close

    override def update { channel.read(buffer) }

    override def poppable = channel.isOpen

    override def tryPop = if(buffer.poppable) buffer.tryPop
    else if(channel.isOpen) { update; buffer.tryPop }
    else None

    override def drop(n: Int) = buffer.drop(n)
    override def expand(n: Int) = buffer.expand(n)
    override def size = buffer.size

    override def get(index: Int) = buffer.get(index)
  }

  def apply[T](buffer: Buffer[T], channel: SinkChannel[T]) = new BufferedSink[T] with Channel {
    override def isOpen = channel.isOpen
    override def close = channel.close

    override def sink = buffer

    override def update { channel.write(buffer) }

    override def tryPush(value: T) = {
      val result = buffer.tryPush(value)
      update
      result
    }

    override def drop(n: Int) = { buffer.expand(n) }
    override def expand(n: Int) = { buffer.drop(n) }
    override def size = buffer.sink.size

    override def set(index: Int, value: T) = buffer.set(index, value)
  }
}

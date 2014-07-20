package basinet

object Chain {
  def apply[SR <: Source[SR, T],
            BSR <: BufferSource[BSR, T],
            BSN <: BufferSink[BSN, T], T]
    (channel: Source[SR, T],
     buffer: Pipe[BSR, BSN, T],
     wire: Wire[SR, BSN])
  = new BufferSource[BSR, T] {
    override def source = buffer.source

    override def isOpen = channel.isOpen
    override def close = channel.close

    override def poppable = channel.isOpen

    override def update = wire.convert(channel.source, buffer.sink)

    override def tryPop = if(buffer.source.poppable)
      buffer.source.tryPop
    else if(channel.isOpen) { update; buffer.source.tryPop }
    else None

    override def drop(n: Int) = buffer.source.drop(n)
    override def expand(n: Int) = buffer.source.expand(n)
    override def size = buffer.source.size

    override def get(index: Int) = buffer.source.get(index)
  }

  def apply[BSR <: BufferSource[BSR, T],
            BSN <: BufferSink[BSN, T],
            SN <: Sink[SN, T], T] 
    (buffer: Pipe[BSR, BSN, T],
     channel: Sink[SN, T],
     wire: Wire[BSR, SN])
  = new BufferSink[BSN, T] {
    override def isOpen = channel.isOpen
    override def close = channel.close

    override def sink = buffer.sink

    override def update = wire.convert(buffer.source, channel.sink)

    override def tryPush(value: T) = {
      val result = buffer.sink.tryPush(value)
      update
      result
    }

    override def drop(n: Int) = buffer.sink.drop(n)
    override def expand(n: Int) =  buffer.sink.expand(n)
    override def size = buffer.sink.size

    override def set(index: Int, value: T) =
      buffer.sink.set(index, value)
  }
}

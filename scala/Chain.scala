package basinet

class Chain[SR <: Source[SR, T], SN <: Sink[SN, U], T, U]
  (source: Source[SR, T], sink: Sink[SN, U], wire: Wire[SR, SN]) extends Channel {
  override def isOpen = source.isOpen || sink.isOpen
  override def _close { source.close; sink.close }

  override def onClose = new evil_ant.Event(true)

  override def update: basinet.Result = {
    val sourceState = source.update
    val state = wire.convert(source, sink)
    if(!source.isOpen) sink.sink.close

    val sinkState = sink.update
    if(!sink.isOpen) source.close

    sourceState.merge(state).merge(sinkState)
  }

  class Handler extends evil_ant.Handler {
    override def absorb(e: evil_ant.IEvent, s: AnyRef) = updateEvents
  }

  private[this] final def setupEvents {
    if(source.isOpen) {
      source.onClose += new evil_ant.Handler {
        override def absorb(e: evil_ant.IEvent, s: AnyRef) = {
          sink.sink.close
          if(!sink.isOpen) close
        }
      }

      source.onPoppable += new Handler
    }

    if(sink.isOpen) { sink.onClose += onClose
      sink.onClose += new evil_ant.Handler {
        override def absorb(e: evil_ant.IEvent, s: AnyRef) = {
          source.source.close
          if(!source.isOpen) close
        }
      }

      sink.onPushable += new Handler
    }
  }

  private[this] final def updateEvents {
    val state = wire.convert(source, sink)

    if(state.isUnderflow) source.onPoppable.enable
    else source.onPoppable.disable

    if(state.isOverflow) sink.onPushable.enable
    else sink.onPushable.disable
  }

  setupEvents
  updateEvents
}

class ChainSource[SR <: Source[SR, T], SN <: Sink[SN, U], SRI <: Source[SRI, V], T, U, V]
  (_source: Source[SR, T], _sink: Pipe[SRI, SN, V, U], wire: Wire[SR, SN])
    extends Chain[SR, SN, T, U](_source, _sink, wire) with Source[SRI, V] {
  override def source = _sink.source

  override def poppable = _sink.poppable | _source.poppable
  override def tryPop = _sink.tryPop

  override def onPoppable = _sink.onPoppable
}

class ChainSink[SR <: Source[SR, T], SN <: Sink[SN, U], SNI <: Sink[SNI, V], T, U, V]
  (_source: Pipe[SR, SNI, T, V], _sink: Sink[SN, U], wire: Wire[SR, SN])
    extends Chain[SR, SN, T, U](_source, _sink, wire) with Sink[SNI, V] {
  override def sink = _source.sink

  override def pushable = _source.pushable || _sink.pushable
  override def tryPush(value: V) = _source.tryPush(value)

  override def onPushable = _source.onPushable
}

class ChainPipe[SR <: Source[SR, T], SN <: Sink[SN, U], SRI <: Source[SRI, V], SNI <: Sink[SNI, W],
                T, U, V, W]
  (_source: Pipe[SR, SNI, T, W], _sink: Pipe[SRI, SN, V, U], wire: Wire[SR, SN])
    extends Chain[SR, SN, T, U](_source, _sink, wire) with Pipe[SRI, SNI, V, W] {
  override def sink = _source.sink
  override def source = _sink.source

  override def poppable = _sink.poppable || _source.poppable
  override def pushable = _source.pushable || _sink.pushable

  override def tryPop = _sink.tryPop
  override def tryPush(value: W) = _source.tryPush(value)

  override def onPoppable = _sink.onPoppable
  override def onPushable = _source.onPushable
}

package basinet

import java.nio.channels.SelectableChannel

trait Channel extends java.io.Closeable {
  def isOpen: Boolean
  def close: Unit = { if(isOpen) onClose.emit(this); _close }
  protected def _close: Unit

  def onClose: evil_ant.IEvent

  def requireOpen = if(!isOpen) throw new java.nio.channels.ClosedChannelException

  def update: Result = Result.NOTHING
}

trait ChannelLike extends Channel {
  private[this] var _isOpen = true
  override def isOpen = _isOpen
  override def _close { _isOpen = false }
  override final val onClose = new evil_ant.Event(true)
}

trait Source[SR <: Source[SR, T], T] extends Channel {
  def source: SR

  def poppable: Boolean = isOpen

  def tryPop: scala.Option[T]

  def pop: T = {
    var result = tryPop

    while(result.isEmpty) { update; result = tryPop }
    result.get
  }

  def popIn(milliseconds: Int): scala.Option[T] = {
    var result = tryPop
    val startTime = System.currentTimeMillis

    while(result.isEmpty && System.currentTimeMillis < startTime + milliseconds && poppable) {
      update
      result = tryPop
    }

    result
  }

  def onPoppable: evil_ant.IEvent

  override def close { if(onPoppable != null) onPoppable.close; super.close }
}

trait Sink[SN <: Sink[SN, T], T] extends Channel {
  def sink: SN

  def pushable: Boolean = isOpen
  def tryPush(value: T): Boolean

  def push(value: T): Unit = while(!tryPush(value)) update
  def pushIn(value: T, milliseconds: Int): Boolean = {
    val startTime = System.currentTimeMillis
    var result = false
    while(!result && (System.currentTimeMillis - startTime) < milliseconds && pushable) {
      update
      result = tryPush(value)
    }
    result
  }

  def onPushable: evil_ant.IEvent

  override def close { if(onPushable != null) onPushable.close; super.close }
}

trait SourceLike[SR <: SourceLike[SR, T], T] extends Source[SR, T] {
  def _pop: T
  override def tryPop = { requireOpen; if(poppable) Some(_pop) else None }
}

trait SinkLike[SN <: SinkLike[SN, T], T] extends Sink[SN, T] {
  def _push(value: T): Unit
  override def tryPush(value: T) = { requireOpen; if(pushable) { _push(value); true } else false }
}

trait Pipe[SR <: Source[SR, T], SN <: Sink[SN, U], T, U] extends Source[SR, T] with Sink[SN, U]

trait PipeLike[SR <: Source[SR, T], SN <: Sink[SN, U], T, U] extends Pipe[SR, SN, T, U] {
  def pipeSource: Source[SR, T] = source
  def pipeSink: Sink[SN, U] = sink

  override def isOpen = pipeSource.isOpen || pipeSink.isOpen
  override def _close { pipeSource.close; pipeSink.close }
  override final lazy val onClose = {
    val event = new evil_ant.WhenEveryEvent
    if(pipeSource.isOpen) pipeSource.onClose += event
    if(pipeSink.isOpen) pipeSink.onClose += event

    event
  }

  override def push(value: U) = pipeSink.push(value)
  override def pushIn(value: U, milliseconds: Int) = pipeSink.pushIn(value, milliseconds)
  override def tryPush(value: U) = pipeSink.tryPush(value)

  override def pop = pipeSource.pop
  override def popIn(milliseconds: Int) = pipeSource.popIn(milliseconds)
  override def tryPop = pipeSource.tryPop

  override def poppable = pipeSource.poppable
  override def pushable = pipeSink.pushable

  override def update = pipeSource.update.merge(pipeSink.update)

  override def onPoppable = pipeSource.onPoppable
  override def onPushable = pipeSink.onPushable
}

class PipeOf[SR <: Source[SR, T], SN <: Sink[SN, U], T, U]
  (_source: Source[SR, T], _sink: Sink[SN, U]) extends PipeLike[SR, SN, T, U] {
  override def source = _source.source
  override def pipeSource = _source

  override def sink = _sink.sink
  override def pipeSink = _sink
}

object PipeOf {
  def apply[SR <: Source[SR, T], SN <: Sink[SN, U], T, U]
    (source: Source[SR, T], sink: Sink[SN, U])
  = new PipeOf[SR, SN, T, U](source, sink)
}

abstract class Wire[SR <: Source[SR, _], SN <: Sink[SN, _]] {
  def convert(from: Source[SR, _], to: Sink[SN, _]): Result = {
    if(!from.isOpen || !to.isOpen)
      Result.NOTHING.underflow(!from.isOpen).overflow(!to.isOpen)
    else _convert(from.source, to.sink)
  }

  protected def _convert(from: SR, to: SN): Result
}

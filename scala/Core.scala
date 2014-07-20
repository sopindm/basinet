package basinet

import java.nio.channels.SelectableChannel

trait Channel extends java.io.Closeable {
  def isOpen: Boolean
  def close: Unit

  def requireOpen = if(!isOpen) throw new java.nio.channels.ClosedChannelException

  def update: Result = Result.NOTHING
}

trait ChannelLike extends Channel {
  private[this] var _isOpen = true
  override def isOpen = _isOpen
  override def close { _isOpen = false }
}

trait Source[SR <: Source[SR, T], T] extends Channel {
  def source: SR

  def poppable: Boolean = isOpen

  def tryPop: scala.Option[T]

  def pop: T = {
    var result = tryPop

    while(result.isEmpty) result = tryPop
    result.get
  }

  def popIn(milliseconds: Int): scala.Option[T] = {
    var result = tryPop
    val startTime = System.currentTimeMillis

    while(result.isEmpty && System.currentTimeMillis < startTime + milliseconds && poppable)
      result = tryPop

    result
  }

  override def update = if(!poppable) Result.UNDERFLOW else super.update
}

trait Sink[SN <: Sink[SN, T], T] extends Channel {
  def sink: SN

  def pushable: Boolean = isOpen
  def tryPush(value: T): Boolean

  def push(value: T): Unit = while(!tryPush(value)) {}
  def pushIn(value: T, milliseconds: Int): Boolean = {
    val startTime = System.currentTimeMillis
    var result = false
    while(!result && (System.currentTimeMillis - startTime) < milliseconds && pushable)
      result = tryPush(value)
    result
  }

  override def update = if(!pushable) Result.OVERFLOW else super.update
}

trait SourceLike[SR <: SourceLike[SR, T], T] extends Source[SR, T] {
  def _pop: T
  override def tryPop = { requireOpen; if(poppable) Some(_pop) else None }
}

trait SinkLike[SN <: SinkLike[SN, T], T] extends Sink[SN, T] {
  def _push(value: T): Unit
  override def tryPush(value: T) = { requireOpen; if(pushable) { _push(value); true } else false }
}

trait Pipe[SR <: Source[SR, T], SN <: Sink[SN, T], T]
    extends Source[SR, T] with Sink[SN, T] {
  override def isOpen = source.isOpen || sink.isOpen
  override def close { source.close; sink.close }

  override def push(value: T) = sink.push(value)
  override def pushIn(value: T, milliseconds: Int) = sink.pushIn(value, milliseconds)
  override def tryPush(value: T) = sink.tryPush(value)

  override def pop = source.pop
  override def popIn(milliseconds: Int) = source.popIn(milliseconds)
  override def tryPop = source.tryPop

  override def poppable = source.poppable
  override def pushable = sink.pushable
}

class PipeOf[SR <: Source[SR, T], SN <: Sink[SN, T], T]
  (_source: Source[SR, T], _sink: Sink[SN, T])
    extends Pipe[SR, SN, T] {
  override def source = _source.source
  override def sink = _sink.sink
}

object PipeOf {
  def apply[SR <: Source[SR, T], SN <: Sink[SN, T], T]
    (source: Source[SR, T], sink: Sink[SN, T])
  = new PipeOf[SR, SN, T](source, sink)
}

abstract class Wire[SR <: Source[SR, _], SN <: Sink[SN, _]] {
  def convert(from: Source[SR, _], to: Sink[SN, _]): Result =
    _convert(from.source, to.sink)

  protected def _convert(from: SR, to: SN): Result
}

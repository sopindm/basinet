package basinet

import java.nio.channels.SelectableChannel

trait Channel extends java.io.Closeable {
  def isOpen: Boolean
  def close: Unit

  def update: Unit = ()
}

trait Source[T] {
  def source: Source[T]
  def poppable: Boolean
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
}

trait SourceChannel[T] extends Source[T] with Channel {
  private[this] def tryRead(buffer: Buffer[T]): Boolean = {
    if(!buffer.sink.pushable) false
    else tryPop match {
      case Some(value) => { buffer.push(value); true }
      case None => false
    }
  }

  def read(buffer: Buffer[T]): Int = {
    var read = 0
    while(tryRead(buffer)) read += 1
    read
  }

  override def poppable = isOpen
}

trait Sink[T] {
  def sink: Sink[T]
  def pushable: Boolean
  def tryPush(value: T): Boolean

  def push(value: T): Unit = while(!tryPush(value)) {}
  def pushIn(value: T, milliseconds: Int): Boolean = {
    val startTime = System.currentTimeMillis
    var result = false
    while(!result && (System.currentTimeMillis - startTime) < milliseconds && pushable)
      result = tryPush(value)
    result
  }
}

trait SinkChannel[T] extends Sink[T] with Channel {
  override def pushable = isOpen
  def write(buffer: Buffer[T]): Int = throw new UnsupportedOperationException
}

trait Pipe[T] extends Source[T] with Sink[T] {
  override def push(value: T) = sink.push(value)
  override def pushIn(value: T, milliseconds: Int) = sink.pushIn(value, milliseconds)
  override def tryPush(value: T) = sink.tryPush(value)

  override def pop = source.pop
  override def popIn(milliseconds: Int) = source.popIn(milliseconds)
  override def tryPop = source.tryPop

  override def poppable = source.poppable
  override def pushable = sink.pushable
}

trait PipeChannel[T] extends Pipe[T] with Channel {
  override def source: SourceChannel[T]
  override def sink: SinkChannel[T]
}

class PipeOf[T](override val source: SourceChannel[T], override val sink: SinkChannel[T])
    extends PipeChannel[T]{
  override def isOpen = source.isOpen || sink.isOpen
  override def close { source.close; sink.close }
}

object PipeOf {
  def apply[T](source: SourceChannel[T], sink: SinkChannel[T]) = new PipeOf[T](source, sink)
}

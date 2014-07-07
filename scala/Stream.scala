package basinet

import java.nio.channels.SelectableChannel

trait Channel extends java.io.Closeable {
  def isOpen: Boolean
  def close: Unit
}

trait Source[T] {
  def pop: T
  def popIn(milliseconds: Int): scala.Option[T]
  def tryPop: scala.Option[T]
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
}

trait SourceLike[T] extends Source[T] {
  override def pop: T = {
    var result = tryPop

    while(result.isEmpty) result = tryPop
    result.get
  }

  def poppable: Boolean = true

  override def popIn(milliseconds: Int) = {
    var result = tryPop
    val startTime = System.currentTimeMillis

    while(result.isEmpty && System.currentTimeMillis < startTime + milliseconds && poppable)
      result = tryPop

    result
  }
}

trait SourceChannelLike[T] extends SourceChannel[T] with SourceLike[T] {
  override def poppable = isOpen
}

trait Sink[T] {
  def push(value: T): Unit
  def pushIn(value: T, milliseconds: Int): Boolean
  def tryPush(value: T): Boolean
}

trait SinkChannel[T] extends Sink[T] with Channel {
  def write(buffer: Buffer[T]): Int = throw new UnsupportedOperationException
}

trait SinkLike[T] extends Sink[T] {
  override def push(value: T) {
    while(!tryPush(value)) {}
  }

  def pushable: Boolean = true

  override def pushIn(value: T, milliseconds: Int): Boolean = {
    val startTime = System.currentTimeMillis
    var result = false
    while(!result && (System.currentTimeMillis - startTime) < milliseconds && pushable)
      result = tryPush(value)
    result
  }
}

trait SinkChannelLike[T] extends SinkChannel[T] with SinkLike[T] {
  override def pushable = isOpen
}

trait Pipe[T] extends Source[T] with Sink[T] {
  def source: Source[T]
  def sink: Sink[T]

  override def push(value: T) = sink.push(value)
  override def pushIn(value: T, milliseconds: Int) = sink.pushIn(value, milliseconds)
  override def tryPush(value: T) = sink.tryPush(value)

  override def pop = source.pop
  override def popIn(milliseconds: Int) = source.popIn(milliseconds)
  override def tryPop = source.tryPop
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

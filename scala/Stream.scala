package basinet

import java.nio.channels.SelectableChannel

trait Stream extends java.io.Closeable {
  def isOpen: Boolean
  def close: Unit
}

trait Source[T] extends Stream {
  def pop: T
  def popIn(milliseconds: Int): scala.Option[T]
  def tryPop: scala.Option[T]
}

trait SourceLike[T] extends Source[T] {
  override def pop: T = {
    var result = tryPop

    while(result.isEmpty) result = tryPop
    result.get
  }

  override def popIn(milliseconds: Int) = {
    var result = tryPop
    val startTime = System.currentTimeMillis

    while(result.isEmpty && System.currentTimeMillis < startTime + milliseconds) result = tryPop
    result
  }
}

trait Sink[T] extends Stream {
  def push(value: T): Unit
  def pushIn(value: T, milliseconds: Int): Boolean
  def tryPush(value: T): Boolean
}

trait SinkLike[T] extends Sink[T] {
  override def push(value: T) {
    while(!tryPush(value)) {}
  }

  override def pushIn(value: T, milliseconds: Int): Boolean = {
    val startTime = System.currentTimeMillis
    var result = false
    while(!result && (System.currentTimeMillis - startTime) < milliseconds) result = tryPush(value)
    result
  }
}

trait Socket[T] extends Stream {
  def source: Source[T]
  def sink: Sink[T]
}

class SocketOf[T](override val source: Source[T], override val sink: Sink[T]) extends Socket[T]{
  override def isOpen = source.isOpen || sink.isOpen
  override def close { source.close; sink.close }
}

object SocketOf {
  def apply[T](source: Source[T], sink: Sink[T]) = new SocketOf[T](source, sink)
}

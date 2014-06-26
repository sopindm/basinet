package basinet

import java.nio.channels.{Pipe => NIOPipe}

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

class PipeSource(val source: NIOPipe.SourceChannel) extends SourceLike[Byte] {
  override def isOpen = source.isOpen
  override def close = source.close

  override def tryPop = {
    val buffer = java.nio.ByteBuffer.allocate(1)
    buffer.position(0).limit(1)

    source.read(buffer)

    if(buffer.position != 0) Some[Byte](buffer.get(0)) else None
  }
}

class PipeSink(val sink: NIOPipe.SinkChannel) extends SinkLike[Byte] {
  override def isOpen = sink.isOpen
  override def close = sink.close

  override def tryPush(value: Byte) = {
    val buffer = java.nio.ByteBuffer.allocate(1)
    buffer.put(value)

    buffer.position(0).limit(1)
    sink.write(buffer)

    buffer.position != 0
  }
}

class Pipe {
  private[this] val pipe = NIOPipe.open
  pipe.source.configureBlocking(false)
  pipe.sink.configureBlocking(false)
  
  def source = new PipeSource(pipe.source)
  def sink = new PipeSink(pipe.sink)
}

package basinet.nio

import basinet.{Result}
import java.nio.channels.{Pipe => JPipe, Channel => JChannel, _}
import java.net._
import scala.Option
import scala.annotation.tailrec

class Channel(channel: JChannel) extends basinet.Channel {
  private def _asSelectable: Option[SelectableChannel] = if(channel.isInstanceOf[SelectableChannel])
    Some(channel.asInstanceOf[SelectableChannel]) else None

  _asSelectable match { case Some(channel) => channel.configureBlocking(false); case _ => () }
  
  override def _close { if(isOpen) channel.close }
  override def isOpen = channel.isOpen

  override val onClose = new evil_ant.Event(true)

  def _onPoppable = _asSelectable match {
    case Some(channel) => new evil_ant.SelectorSignal(channel, SelectionKey.OP_READ, false)
    case None => null
  }

  def _onPushable = _asSelectable match {
    case Some(channel) => new evil_ant.SelectorSignal(channel, SelectionKey.OP_WRITE, false)
    case None => null
  }
}

abstract class Source[T](channel: JChannel) extends Channel(channel)
    with basinet.Source[Source[T], T] {
  override def source = this
}

/*
abstract class Sink[T](channel: JChannel) extends Channel(channel)
    with basinet.Sink[Sink[T], T] {
  override def sink = this
}*/

class ByteSource(val channel: ReadableByteChannel)
    extends Channel(channel) with basinet.Source[ByteSource, Byte] {
  override def source = this
  def eof = close

  override def tryPop:Option[Byte] = {
    if(!isOpen) throw new ClosedChannelException
    val buffer = byte.Buffer(1)
    if(ByteChannelReader.convert(this, buffer.sink).isOverflow)
      Some(buffer.pop)
    else None
  }

  override val onPoppable = _onPoppable
}

class ByteSink(val channel: WritableByteChannel)
    extends Channel(channel) with basinet.Sink[ByteSink, Byte] {
  override def sink = this
  override def tryPush(value: Byte) = {
    if(!isOpen) throw new ClosedChannelException
    val buffer = byte.Buffer(1); buffer.push(value)
    ByteChannelWriter.convert(buffer, this).isUnderflow
  }

  override val onPushable = _onPushable
}

object Pipe {
  def apply = {
    val pipe = java.nio.channels.Pipe.open
    basinet.PipeOf(new ByteSource(pipe.source), new ByteSink(pipe.sink))
  }
}

trait TcpAddress {
  def host: String
  def port: Int
  def ip: String
}

object TcpAddress {
  def apply(address: java.net.SocketAddress) = address match {
    case inet: java.net.InetSocketAddress => new TcpAddress {
      override def host = inet.getHostName
      override def port = inet.getPort
      override def ip = inet.getAddress.getHostAddress
    }
    case _ => throw new IllegalArgumentException
  }
}

trait TcpAddressable {
  def localAddress: Option[TcpAddress]
  def remoteAddress: Option[TcpAddress]
}

class TcpSocket(channel: java.nio.channels.SocketChannel)
    extends basinet.PipeLike[ByteSource, ByteSink, Byte, Byte]
    with TcpAddressable {
  self: TcpSocket =>

  channel.setOption[java.lang.Boolean](
    java.net.StandardSocketOptions.TCP_NODELAY, true)

  @volatile
  private[this] var _readable = true
  @volatile
  private[this] var _writable = true

  override def _close { channel.close }
  override def isOpen = channel.isOpen

  override def localAddress = { val address = channel.getLocalAddress
    if(address != null) Some(TcpAddress(address)) else None
  }

  override def remoteAddress =  { val address = channel.getRemoteAddress
    if(address != null) Some(TcpAddress(address)) else None
  }

  override val source = new ByteSource(channel) with TcpAddressable {
    override def isOpen = self.isOpen && _readable

    override def eof = self.close

    override def _close {
      _readable = false
      if(!_writable) channel.close
    }

    override def localAddress = self.localAddress
    override def remoteAddress = self.remoteAddress
  }

  override val sink = new ByteSink(channel) with TcpAddressable {
    override def isOpen = self.isOpen && _writable

    override def _close {
      _writable = false
      if(!_readable) channel.close
    }

    override def localAddress = self.localAddress
    override def remoteAddress = self.remoteAddress
  }
}

object TcpSocket {
  def apply(channel: SocketChannel) = new TcpSocket(channel)
}

class TcpAcceptor(channel: ServerSocketChannel) 
    extends Source[TcpSocket](channel) with TcpAddressable {
  override def tryPop = {
    val socket = channel.accept
    if(socket != null) Some(TcpSocket(socket)) else None
  }

  override def localAddress = Some(TcpAddress(channel.getLocalAddress))
  override def remoteAddress = None

  override val onPoppable = new evil_ant.SelectorSignal(channel, SelectionKey.OP_ACCEPT, false)
}

class TcpConnector(channel: SocketChannel, remote: SocketAddress)
    extends Source[TcpSocket](channel) with TcpAddressable {
  var connected = channel.connect(remote)
  var read = false

  override def isOpen = !read && super.isOpen

  override def tryPop = {
    if(!connected) connected = channel.finishConnect()
    if(connected) { read = true; Some(TcpSocket(channel)) } else None
  }

  override def localAddress = {
    val address = channel.getLocalAddress
    if(address != null) Some(TcpAddress(address)) else None
  }

  override def remoteAddress = {
    val address = channel.getRemoteAddress
    if(address != null) Some(TcpAddress(address)) else None
  }

  override val onPoppable = new evil_ant.SelectorSignal(channel, SelectionKey.OP_CONNECT, false)
}

object ByteChannelReader
    extends basinet.Wire[ByteSource, BufferSink[java.nio.ByteBuffer, Byte]] {
  @tailrec
  private[this]
  def read(source: ByteSource, sink: BufferSink[java.nio.ByteBuffer, Byte], totalRead: Int): Int = {
    var readLast = source.channel.read(sink.buffer.duplicate)
    if(readLast < 0) { source.eof; totalRead }
    else if(readLast == 0) totalRead
    else { sink.drop(readLast); read(source, sink, totalRead + readLast) }
  }

  override def _convert(source: ByteSource, sink: BufferSink[java.nio.ByteBuffer, Byte]) = {
    sink.requireOpen
    val got = read(source, sink, 0)

    if(sink.size > 0) basinet.Result.UNDERFLOW
    else basinet.Result.OVERFLOW
  }
}

object ByteChannelWriter
    extends basinet.Wire[BufferSource[java.nio.ByteBuffer, Byte], ByteSink] {
  @tailrec
  private[this]
  def write(sink: ByteSink, source: BufferSource[java.nio.ByteBuffer, Byte], totalWriten: Int): Int = {
    var writen = sink.channel.write(source.buffer.duplicate)
    if(writen == 0) totalWriten
    else { source.drop(writen); write(sink, source, totalWriten + writen) }
  }

  override def _convert(source: BufferSource[java.nio.ByteBuffer, Byte], sink: ByteSink) = {
    source.requireOpen
    val got = write(sink, source, 0)

    if(source.size > 0) basinet.Result.OVERFLOW
    else basinet.Result.UNDERFLOW
  }
}

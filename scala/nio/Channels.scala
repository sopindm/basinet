package basinet.nio

import java.nio.channels.{Pipe => JPipe, Channel => JChannel, _}
import java.net._
import scala.Option
import scala.annotation.tailrec

class Channel(channel: JChannel) extends basinet.Channel {
  if(channel.isInstanceOf[SelectableChannel])
    channel.asInstanceOf[SelectableChannel].configureBlocking(false)

  override def close { if(isOpen) channel.close }
  override def isOpen = channel.isOpen
}

abstract class Source[T](channel: JChannel) extends Channel(channel)
    with basinet.Source[Source[T], T] {
  override def source = this
}

abstract class Sink[T](channel: JChannel) extends Channel(channel)
    with basinet.Sink[Sink[T], T] {
  override def sink = this
}

class ByteSource(val channel: ReadableByteChannel)
    extends Channel(channel) with basinet.Source[ByteSource, Byte] {
  override def source = this
  def eof = close

  override def tryPop:Option[Byte] = {
    val buffer = java.nio.ByteBuffer.allocate(1)
    channel.read(buffer) match {
      case -1 => { eof; None }
      case 1 => Some(buffer.get(0))
      case _ => None
    }
  }
}

class ByteSink(val channel: WritableByteChannel)
    extends Channel(channel) with basinet.Sink[ByteSink, Byte] {
  override def sink = this
  override def tryPush(value: Byte) = {
    val buffer = java.nio.ByteBuffer.allocate(1)
    buffer.put(value)
    buffer.position(0).limit(1)

    channel.write(buffer) == 1
  }
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
    extends basinet.Pipe[ByteSource, ByteSink, Byte]
    with TcpAddressable {
  self: TcpSocket =>

  channel.setOption[java.lang.Boolean](
    java.net.StandardSocketOptions.TCP_NODELAY, true)

  @volatile
  private[this] var _readable = true
  @volatile
  private[this] var _writable = true

  override def close { channel.close }
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

    override def close {
      _readable = false
      if(!_writable) channel.close
    }

    override def localAddress = self.localAddress
    override def remoteAddress = self.remoteAddress
  }

  override val sink = new ByteSink(channel) with TcpAddressable {
    override def isOpen = self.isOpen && _writable

    override def close {
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
}

object ByteChannelReader
    extends basinet.Wire[ByteSource, bytebuffer.Sink] {
  @tailrec
  private[this]
  def read(source: ByteSource,
           buffer: java.nio.ByteBuffer, 
           totalRead: Int): Int = {
    var readLast = source.channel.read(buffer)
    if(readLast < 0) { source.eof; totalRead }
    else if(readLast == 0) totalRead
    else read(source, buffer, totalRead + readLast)
  }

  override def _convert(source: ByteSource, sink: bytebuffer.Sink) = {
    val got = read(source, sink.buffer.duplicate, 0)
    sink.drop(got)
    if(sink.size > 0) basinet.Result.UNDERFLOW
    else basinet.Result.OVERFLOW
  }
}

object ByteChannelWriter
    extends basinet.Wire[bytebuffer.Source, ByteSink] {
  @tailrec
  private[this]
  def write(sink: ByteSink,
            buffer: java.nio.ByteBuffer,
            totalWriten: Int): Int = {
    var writen = sink.channel.write(buffer)
    if(writen == 0) totalWriten
    else write(sink, buffer, totalWriten + writen)
  }

  override def _convert(source: bytebuffer.Source, sink: ByteSink) = {
    val got = write(sink, source.buffer.duplicate, 0)
    source.drop(got)
    if(source.size > 0) basinet.Result.OVERFLOW
    else basinet.Result.UNDERFLOW
  }
}

package basinet

import java.nio.channels.{Pipe => NIOPipe, _}
import java.net._

class NIOStream(channel: SelectableChannel) extends Stream {
  channel.configureBlocking(false)

  override def close { if(isOpen) channel.close }
  override def isOpen = channel.isOpen
}

abstract class NIOSource[T](channel: SelectableChannel) extends NIOStream(channel) with SourceLike[T]
abstract class NIOSink[T](channel: SelectableChannel) extends NIOStream(channel) with SinkLike[T]

class NIOByteSource[T <: ReadableByteChannel with SelectableChannel](channel: T)
    extends NIOSource[Byte](channel) {
  override def tryPop = {
    val buffer = java.nio.ByteBuffer.allocate(1)
    buffer.position(0).limit(1)

    channel.read(buffer)

    if(buffer.position != 0) Some[Byte](buffer.get(0)) else None
  }
}

class NIOByteSink[T <: WritableByteChannel with SelectableChannel](channel: T)
    extends NIOSink[Byte](channel) {
  override def tryPush(value: Byte) = {
    val buffer = java.nio.ByteBuffer.allocate(1)
    buffer.put(value)

    buffer.position(0).limit(1)
    channel.write(buffer)

    buffer.position != 0
  }
}

object Pipe {
  def apply = {
    val pipe = NIOPipe.open
    SocketOf(new NIOByteSource(pipe.source), new NIOByteSink(pipe.sink))
  }
}

object TcpSocket {
  def apply(channel: SocketChannel) = SocketOf(new NIOByteSource(channel), new NIOByteSink(channel))
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
  def localAddress: scala.Option[TcpAddress]
  def remoteAddress: scala.Option[TcpAddress]
}

class TcpAcceptor(channel: ServerSocketChannel) 
    extends NIOSource[Socket[Byte]](channel) with TcpAddressable {
  override def tryPop = {
    val socket = channel.accept

    if(socket != null) Some(TcpSocket(socket)) else None
  }

  override def localAddress = Some(TcpAddress(channel.getLocalAddress))
  override def remoteAddress = None
}

class TcpConnector(channel: SocketChannel, remote: SocketAddress)
    extends NIOSource[Socket[Byte]](channel) with TcpAddressable {
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

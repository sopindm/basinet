package basinet

import java.nio.channels.{Pipe => NIOPipe, _}
import java.net._

class NIOChannel(channel: SelectableChannel) extends Channel {
  channel.configureBlocking(false)

  override def close { if(isOpen) channel.close }
  override def isOpen = channel.isOpen
}

abstract class NIOSource[T](channel: SelectableChannel) extends NIOChannel(channel)
    with SourceChannelLike[T]
abstract class NIOSink[T](channel: SelectableChannel) extends NIOChannel(channel)
    with SinkChannelLike[T]

class NIOByteSource[T <: ReadableByteChannel with SelectableChannel](channel: T)
    extends NIOSource[Byte](channel) {
  protected def eof() { close() }

  override def tryPop = {
    val buffer = java.nio.ByteBuffer.allocate(1)
    buffer.position(0).limit(1)

    val read = channel.read(buffer)
    if(read == -1) eof()

    if(buffer.position != 0) Some[Byte](buffer.get(0)) else None
  }

  override def read(buffer: Buffer[Byte]) = buffer match {
    case bb: ByteBuffer => { val read = channel.read(bb.sink.buffer); bb.source.expand(read); read }
    case _ => super.read(buffer)
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

  override def write(buffer: Buffer[Byte]) = buffer match {
    case bb: ByteBuffer => {
      val writen = channel.write(bb.source.buffer)
      //bb.sink.expand(writen)
      writen
    }
    case _ => super.write(buffer)
  }
}

object Pipe {
  def apply = {
    val pipe = NIOPipe.open
    SocketOf(new NIOByteSource(pipe.source), new NIOByteSink(pipe.sink))
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
  def localAddress: scala.Option[TcpAddress]
  def remoteAddress: scala.Option[TcpAddress]
}

class TcpSocket(channel: java.nio.channels.SocketChannel) extends Socket[Byte] with TcpAddressable {
  self: TcpSocket =>

  channel.setOption[java.lang.Boolean](java.net.StandardSocketOptions.TCP_NODELAY, true)

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

  override val source = new NIOByteSource(channel) with TcpAddressable {
    override def isOpen = self.isOpen && _readable

    override def close {
      _readable = false
      if(!_writable) channel.close
    }

    override def eof { channel.close }

    override def localAddress = self.localAddress
    override def remoteAddress = self.remoteAddress
  }

  override val sink = new NIOByteSink(channel) with TcpAddressable {
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

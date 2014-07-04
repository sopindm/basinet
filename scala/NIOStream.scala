package basinet

import java.nio.channels.{Pipe => NIOPipe, _}
import java.net._
import scala.annotation.tailrec

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
    val buffer = ByteBuffer(1)
    if(read(buffer) != 0) Some[Byte](buffer.pop) else None
  }

  @tailrec
  private[this] def read(buffer: ByteBuffer, readBefore: Int): Int = {
    if(!buffer.sink.pushable) return readBefore

    val readNow = channel.read(buffer.sink.buffer)
    if(readNow == -1) { eof; return readBefore }
    if(readNow == 0) return readBefore

    buffer.sink.compact
    buffer.source.expand(readNow)
    read(buffer, readBefore + readNow)
  }

  override def read(buffer: Buffer[Byte]) = buffer match {
    case bb: ByteBuffer => read(bb, 0)
    case _ => super.read(buffer)
  }
}

class NIOByteSink[T <: WritableByteChannel with SelectableChannel](channel: T)
    extends NIOSink[Byte](channel) {
  override def tryPush(value: Byte) = {
    val buffer = ByteBuffer(1)
    buffer.push(value)

    write(buffer) == 1
  }

  @tailrec
  private[this] def write(buffer: ByteBuffer, writenBefore: Int): Int = {
    if(!buffer.source.poppable) return writenBefore
    
    val writen = channel.write(buffer.source.buffer)
    if(writen == 0) return writenBefore

    buffer.source.compact
    buffer.sink.expand(writen)
    write(buffer, writenBefore + writen)
  }

  override def write(buffer: Buffer[Byte]) = buffer match {
    case bb: ByteBuffer => write(bb, 0)
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

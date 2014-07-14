package basinet.nio

import java.nio.{Buffer => JBuffer,ByteBuffer=>JByteBuffer, CharBuffer => JCharBuffer}
import java.nio.charset._
import scala.annotation.tailrec

class Buffer[T <: JBuffer](buffer: T) extends basinet.BufferLike
    with basinet.ChannelLike {
  override def begin = buffer.position
  override def end = buffer.limit
  override def capacity = buffer.capacity

  override def reset(newBegin: Int, newEnd: Int) =
    if(newBegin < end) { buffer.position(newBegin); buffer.limit(newEnd) }
    else { buffer.limit(newEnd); buffer.position(newBegin) }
}

abstract class BufferSource[B <: JBuffer, T]
  (val buffer: B, pipe: basinet.Pipe[BufferSource[B, T], BufferSink[B, T], T])
    extends Buffer[B](buffer)
    with basinet.BufferSourceLike[BufferSource[B, T], BufferSink[B, T], T] {
  override def source = this
  override def sink = pipe.sink
}

abstract class BufferSink[B <: JBuffer, T]
  (val buffer: B, pipe: basinet.Pipe[BufferSource[B, T], BufferSink[B, T], T])
    extends Buffer[B](buffer)
    with basinet.BufferSinkLike[BufferSource[B, T], BufferSink[B, T], T] {
  buffer.position(buffer.limit).limit(buffer.capacity)
  drop(0)

  override def sink = this
  override def source = pipe.source
}

abstract class BufferPipe[B <: JBuffer, T](buffer: B)
    extends basinet.Pipe[BufferSource[B, T], BufferSink[B, T], T]

package byte {
  class BufferSource(buffer: JByteBuffer, pipe: basinet.nio.BufferPipe[JByteBuffer, Byte])
      extends basinet.nio.BufferSource[JByteBuffer, Byte](buffer, pipe) {
    override def absoluteGet(index: Int) = buffer.get(index)
  }

  class BufferSink(buffer: JByteBuffer, pipe: basinet.nio.BufferPipe[JByteBuffer, Byte])
      extends basinet.nio.BufferSink[JByteBuffer, Byte](buffer, pipe) {
    override def absoluteSet(index: Int, value: Byte) = buffer.put(index, value)
  }

  class BufferPipe(buffer: java.nio.ByteBuffer)
      extends basinet.nio.BufferPipe[JByteBuffer, Byte](buffer) {
    override val source = new BufferSource(buffer.duplicate, this)
    override val sink = new BufferSink(buffer.duplicate, this)
  }

  object Buffer {
    def apply(n: Int) = {
      val buffer = java.nio.ByteBuffer.allocate(n)
      buffer.limit(0)
      new BufferPipe(buffer)
    }
  }
}

package char {
  class BufferSource(buffer: JCharBuffer, pipe: basinet.nio.BufferPipe[JCharBuffer, Character])
      extends basinet.nio.BufferSource[JCharBuffer, Character](buffer, pipe) {
    override def absoluteGet(index: Int) = buffer.get(index)
  }

  class BufferSink(buffer: JCharBuffer, pipe: basinet.nio.BufferPipe[JCharBuffer, Character])
      extends basinet.nio.BufferSink[JCharBuffer, Character](buffer, pipe) {
    override def absoluteSet(index: Int, value: Character) = buffer.put(index, value)
  }

  class BufferPipe(buffer: java.nio.CharBuffer)
      extends basinet.nio.BufferPipe[JCharBuffer, Character](buffer) {
    override val source = new BufferSource(buffer.duplicate, this)
    override val sink = new BufferSink(buffer.duplicate, this)
  }
}

class CharsetDecoder(charset: Charset)
    extends basinet.Wire[BufferSource[JByteBuffer, Byte], BufferSink[JCharBuffer, Character]] {
  @tailrec
  private[this] def decode(decoder: java.nio.charset.CharsetDecoder,
                           from: BufferSource[JByteBuffer, Byte],
                           to: BufferSink[JCharBuffer, Character]): basinet.Result = {
    val (bytesAt, charsAt) = (from.begin, to.begin)
    val (bytes, chars) = (from.buffer.duplicate, to.buffer.duplicate)
    val result = decoder.decode(bytes, chars, false)

    from.drop(bytes.position - bytesAt)
    to.drop(chars.position - charsAt)

    if(result == CoderResult.UNDERFLOW) {
      if(from.size == (bytes.limit - bytes.position)) basinet.Result.UNDERFLOW
      else decode(decoder, from, to)
    }
    else if(result == CoderResult.OVERFLOW) {
      if(to.size == (chars.limit - chars.position)) basinet.Result.OVERFLOW
      else decode(decoder, from, to)
    }
    else throw new CharacterCodingException
  }

  override def _convert(from: BufferSource[JByteBuffer, Byte],
                        to: BufferSink[JCharBuffer, Character]) =
    decode(charset.newDecoder, from, to)
}

class CharsetEncoder(charset: Charset)
    extends basinet.Wire[BufferSource[JCharBuffer, Character], BufferSink[JByteBuffer, Byte]] {
  @tailrec
  private[this] def encode(encoder: java.nio.charset.CharsetEncoder,
                           from: BufferSource[JCharBuffer, Character],
                           to: BufferSink[JByteBuffer, Byte]): basinet.Result = {
    val (charsAt, bytesAt) = (from.begin, to.begin)
    val (chars, bytes) = (from.buffer.duplicate, to.buffer.duplicate)
    val result = encoder.encode(chars, bytes, false)

    from.drop(chars.position - charsAt)
    to.drop(bytes.position - bytesAt)

    if(result == CoderResult.UNDERFLOW) {
      if(from.size == (chars.limit - chars.position)) basinet.Result.UNDERFLOW
      else encode(encoder, from, to)
    }
    else if(result == CoderResult.OVERFLOW) {
      if(to.size == (bytes.limit - bytes.position)) basinet.Result.OVERFLOW
      else encode(encoder, from, to)
    }
    else throw new CharacterCodingException
  }

  override def _convert(from: BufferSource[JCharBuffer, Character],
                        to: BufferSink[JByteBuffer, Byte]) =
    encode(charset.newEncoder, from, to)
}



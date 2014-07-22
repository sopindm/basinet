package basinet

import scala.annotation.tailrec

object LineWire {
  def isNewline(c: Character) = {
    val code = c.toInt
    if(code >= 10 && code <= 13) true else false
  }
}

package line {
  final class Source {
    private[this] var _builder = new StringBuilder
    private[this] var _complete = false
    private[this] var _endsWithCarriageReturn = false

    def pushable = !_complete
    def push(value: Character) {
      if(LineWire.isNewline(value)) {
        if(_endsWithCarriageReturn && value == '\n') _endsWithCarriageReturn = false
        else {
          _complete = true
          if(value.toInt == 13) _endsWithCarriageReturn = true
        }
      }
      else { _builder.append(value); _endsWithCarriageReturn = false }
    }
    def push(buffer: java.nio.CharBuffer) { _builder.append(buffer) }

    def poppable = _complete
    def pop = {
      val result = _builder.toString
      _builder = new StringBuilder
      _complete = false
      result
    }

    def write(source: nio.BufferSource[java.nio.CharBuffer, Character]) {
      if(!pushable) return
      if(LineWire.isNewline(source.get(0))) push(source.pop)
      else {
        val buffer = source.buffer
        val start = source.buffer.position
        var position = start
        while(position < buffer.limit && !LineWire.isNewline(buffer.get(position)))
          position += 1

        val limitedBuffer = buffer.duplicate
        limitedBuffer.limit(position)

        push(limitedBuffer)
        source.drop(position - start)
      }
    }
  }

  class Sink(newline: String) {
    private[this] var _string: String = null
    private[this] var _position = 0

    def pushable = (_string == null)
    def push(value: String) { _string = value.concat(newline); _position = 0 }

    def poppable = (_string != null)

    def pop(buffer: java.nio.CharBuffer) = {
      val writeLength = scala.math.min(buffer.limit - buffer.position, _string.length - _position)

      buffer.append(_string, _position, _position + writeLength)
      _position += writeLength
      if(_position == _string.length) _string = null

      writeLength
    }
  }
}

class LineWriter extends Wire[nio.BufferSource[java.nio.CharBuffer, Character],
                              any.BufferSink[String]] {
  type Source = nio.BufferSource[java.nio.CharBuffer, Character]
  type Sink = any.BufferSink[String]

  private[this] val buffer = new line.Source

  @tailrec
  private[this] def _write(from: Source, to: Sink): basinet.Result = {
    if(!to.pushable) return Result.OVERFLOW
    if(!from.poppable) return Result.UNDERFLOW

    buffer.write(from)
    if(buffer.poppable) to.push(buffer.pop)

    _write(from, to)
  }

  override def _convert(from: Source, to: Sink) = _write(from, to)
}

class LineReader(newline: String) 
    extends Wire[any.BufferSource[String], nio.BufferSink[java.nio.CharBuffer, Character]] {
  type Source = any.BufferSource[String]
  type Sink = nio.BufferSink[java.nio.CharBuffer, Character]

  private[this] val buffer = new line.Sink(newline)

  @tailrec
  private[this] def _write(from: Source, to: Sink): Result = {
    if(!to.pushable) return Result.OVERFLOW
    if(!from.poppable && !buffer.poppable) return Result.UNDERFLOW

    if(buffer.pushable) buffer.push(from.pop)
    val writen = buffer.pop(to.buffer.duplicate)
    to.drop(writen)

    _write(from, to)
  }

  override def _convert(from: Source, to: Sink) = _write(from, to)
}

package basinet

import scala.annotation.tailrec

object LineChannel {
  def isNewline(c: Character) = {
    val code = c.toInt
    if(code >= 10 && code <= 13) true else false
  }
}

class LineSource extends SourceLike[LineSource, String]
    with SinkLike[LineSource, Character] with ChannelLike {
  override def source = this
  override def sink = this

  private[this] var _builder = new StringBuilder
  private[this] var _complete = false
  private[this] var _endsWithCarriageReturn = false

  override def pushable = super.pushable && !_complete
  override def _push(value: Character) {
    if(LineChannel.isNewline(value)) {
      if(_endsWithCarriageReturn && value == '\n') _endsWithCarriageReturn = false
      else {
        _complete = true
        if(value.toInt == 13) _endsWithCarriageReturn = true
      }
    }
    else { _builder.append(value); _endsWithCarriageReturn = false }
  }

  def push(buffer: java.nio.CharBuffer) { _builder.append(buffer) }

  override def poppable = super.poppable && _complete
  override def _pop = {
    val result = _builder.toString
    _builder = new StringBuilder
    _complete = false
    result
  }
}

object LineWriter extends Wire[nio.BufferSource[java.nio.CharBuffer, Character], LineSource] {
  private[this] def bulkPush(from: nio.BufferSource[java.nio.CharBuffer, Character],
                             to: LineSource) {
    val buffer = from.buffer
    val start = from.buffer.position
    var position = start
    while(position < buffer.limit && !LineChannel.isNewline(buffer.get(position)))
      position += 1

    val limitedBuffer = buffer.duplicate
    limitedBuffer.limit(position)

    to.push(limitedBuffer)
    from.drop(position - start)
  }

  @tailrec
  private[this] def _write(from: nio.BufferSource[java.nio.CharBuffer, Character],
                           to: LineSource): basinet.Result = {
    if(!from.poppable) return Result.UNDERFLOW
    if(!to.pushable) return Result.OVERFLOW

    if(LineChannel.isNewline(from.get(0))) {
      if(to.tryPush(from.get(0))) from.drop(1)
    }
    else bulkPush(from, to)

    _write(from, to)
  }

  override def _convert(source: nio.BufferSource[java.nio.CharBuffer, Character], sink: LineSource) =
    _write(source, sink)
}

class LineSink(newline: String) extends SinkLike[LineSink, String]
    with SourceLike[LineSink, Character] with ChannelLike {
  private[this] var _string: String = null
  private[this] var _position = 0

  override def source = this
  override def sink = this

  override def pushable = super.pushable && _string == null
  override def _push(value: String) { _string = value.concat(newline); _position = 0 }

  override def poppable = super.poppable && _string != null
  override def _pop = {
    val result = _string(_position)
    _position += 1
    if(_position == _string.length) _string = null
    result
  }

  def pop(buffer: java.nio.CharBuffer) = {
    val writeLength = scala.math.min(buffer.limit - buffer.position, _string.length - _position)

    buffer.append(_string, _position, _position + writeLength) 
    _position += writeLength
    if(_position == _string.length) _string = null

    writeLength
  }
}

object LineReader extends Wire[LineSink, nio.BufferSink[java.nio.CharBuffer, Character]] {
  @tailrec
  private[this] def _write(from: LineSink,
                           to: nio.BufferSink[java.nio.CharBuffer, Character]): Result = {
    if(!from.poppable) return Result.UNDERFLOW
    if(!to.pushable) return Result.OVERFLOW

    val writen = from.pop(to.buffer.duplicate)
    to.drop(writen)

    _write(from, to)
  }

  override def _convert(from: LineSink, to: nio.BufferSink[java.nio.CharBuffer, Character]) =
    _write(from, to)
}

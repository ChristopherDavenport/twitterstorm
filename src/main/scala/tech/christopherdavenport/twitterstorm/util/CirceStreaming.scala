package tech.christopherdavenport.twitterstorm.util

import _root_.fs2.{ Pipe, Segment, Stream, Pull }
import _root_.jawn.{ AsyncParser, ParseException }
import io.circe.jawn.CirceSupportParser
import io.circe.{ Json, ParsingFailure, Decoder }

object CirceStreaming {


  // Inhaled Circe-fs2 async parsing pipe



  final def byteStreamParserS[F[_]]: Pipe[F, Segment[Byte, Unit], Json] = byteParserS(AsyncParser.ValueStream)

  final  def byteParserS[F[_]](mode: AsyncParser.Mode): Pipe[F, Segment[Byte, Unit], Json] =
    new ParsingPipe[F, Segment[Byte, Unit]] {
      protected[this] final def parseWith(p: AsyncParser[Json])(in: Segment[Byte, Unit]): Either[ParseException, Seq[Json]] =
        p.absorb(in.force.toArray)(CirceSupportParser.facade)

      protected[this] val parsingMode: AsyncParser.Mode = mode
    }


  private abstract class ParsingPipe[F[_], S] extends Pipe[F, S, Json] {
    protected[this] def parsingMode: AsyncParser.Mode

    protected[this] def parseWith(parser: AsyncParser[Json])(in: S): Either[ParseException, Seq[Json]]

    private[this] final def makeParser: AsyncParser[Json] = CirceSupportParser.async(mode = parsingMode)

    private[this] final def doneOrLoop[A](p: AsyncParser[Json])(s: Stream[F, S]): Pull[F, Json, Unit] =
      s.pull.uncons1.flatMap {
        case Some((s, str)) => parseWith(p)(s) match {
          case Left(error) =>
            Pull.raiseError(ParsingFailure(error.getMessage, error))
          case Right(js) =>
            Pull.output(Segment.seq(js)) >> doneOrLoop(p)(str)
        }
        case None => Pull.done
      }

    final def apply(s: Stream[F, S]): Stream[F, Json] = doneOrLoop(makeParser)(s).stream
  }

  final def stringParser[F[_]](mode: AsyncParser.Mode): Pipe[F, String, Json] = new ParsingPipe[F, String] {
    protected[this] final def parseWith(p: AsyncParser[Json])(in: String): Either[ParseException, Seq[Json]] =
      p.absorb(in)(CirceSupportParser.facade)

    protected[this] val parsingMode: AsyncParser.Mode = mode
  }


  final def stringStreamParser[F[_]]: Pipe[F, String, Json] = stringParser(AsyncParser.ValueStream)

  final def stringArrayParser[F[_]]: Pipe[F, String, Json] = stringParser(AsyncParser.UnwrapArray)

  final def decoder[F[_], A](implicit decode: Decoder[A]): Pipe[F, Json, A] =
    _.flatMap { json =>
      decode(json.hcursor) match {
        case Left(df) => Stream.raiseError(df)
        case Right(a) => Stream.emit(a)
      }
    }

}

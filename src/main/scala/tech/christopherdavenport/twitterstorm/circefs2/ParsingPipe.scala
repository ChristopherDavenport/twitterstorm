package tech.christopherdavenport.twitterstorm.circefs2

import _root_.fs2.{Pipe, Pull, Segment, Stream}
import _root_.jawn.{AsyncParser, ParseException}
import io.circe.{Json, ParsingFailure}
import io.circe.jawn.CirceSupportParser

private[circefs2] abstract class ParsingPipe[F[_], S] extends Pipe[F, S, Json] {
  protected[this] def parsingMode: AsyncParser.Mode

  protected[this] def parseWith(parser: AsyncParser[Json])(in: S): Either[ParseException, Seq[Json]]

  private[this] final def makeParser: AsyncParser[Json] = CirceSupportParser.async(mode = parsingMode)

  private[this] final def doneOrLoop[A](p: AsyncParser[Json])(s: Stream[F, S]): Stream[F, Json] = s.repeatPull {
    _.uncons1.flatMap {
      case Some((hd, tl)) => parseWith(p)(hd) match {
        case Left(error) =>
          Pull.fail(ParsingFailure(error.getMessage, error))
        case Right(js) =>
          Pull.output(Segment.seq(js)).as(Some(tl))
      }
      case None => Pull.pure(None)
    }
  }

  final def apply(s: Stream[F, S]): Stream[F, Json] = doneOrLoop(makeParser)(s)

}
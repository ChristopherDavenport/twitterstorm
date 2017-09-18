package tech.christopherdavenport.twitterstorm

import cats.implicits._
import cats.Show
import cats.effect.{Effect, Sync}
import fs2.{Pipe, Sink, Stream}
import io.circe.{Decoder, Encoder, HCursor, Json}
import org.log4s.Logger

package object util {

  def printSink[F[_]: Effect, A](implicit F: Sync[F]): Sink[F, A] =
    _.evalMap(v => F.delay(println(v)))

  def logSink[F[_]: Effect, A](logger: Logger, logLevel: LogLevel = TRACE, log : A => String)(implicit F: Effect[F]): Sink[F, A] =
    _.evalMap(a => logLevel match {
      case ERROR => F.delay(logger.error(log(a)))
      case WARN => F.delay(logger.warn(log(a)))
      case INFO => F.delay(logger.info(log(a)))
      case TRACE => F.delay(logger.trace(log(a)))
    })

  def filterLeft[F[_], A, B]: Pipe[F, Either[A, B], B] = _.flatMap {
    case Right(r) => Stream.emit(r)
    case Left(_) => Stream.empty
  }

  def filterRight[F[_], A, B]: Pipe[F, Either[A, B], A] = _.flatMap {
    case Left(e) => Stream.emit(e)
    case Right(_) => Stream.empty
  }

}

sealed trait LogLevel
case object ERROR extends LogLevel
case object WARN extends LogLevel
case object INFO extends LogLevel
case object TRACE extends LogLevel


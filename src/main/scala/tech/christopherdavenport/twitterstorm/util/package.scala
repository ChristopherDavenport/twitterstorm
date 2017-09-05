package tech.christopherdavenport.twitterstorm

import cats.effect.{Effect, Sync}
import fs2.{Pipe, Sink, Stream}

package object util {

  def printSink[F[_]: Effect, A](implicit F: Sync[F]): Sink[F, A] = s => s.flatMap( value =>
    Stream.eval(F.delay(println(value)))
  )
  def filterLeft[F[_], A, B] : Pipe[F, Either[A, B], B] = _.flatMap{
    case Right(r) => Stream.emit(r)
    case Left(_) => Stream.empty
  }

  def filterRight[F[_], A, B] : Pipe[F, Either[A, B], A] = _.flatMap{
    case Left(e) => Stream.emit(e)
    case Right(_) => Stream.empty
  }

}

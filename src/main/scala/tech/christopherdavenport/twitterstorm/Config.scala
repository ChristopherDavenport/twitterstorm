package tech.christopherdavenport.twitterstorm

import cats.effect.Effect
import cats.implicits._
import fs2.Stream
import pureconfig.error.ConfigReaderFailures
import pureconfig.loadConfig
import tech.christopherdavenport.twitterstorm.authentication.TwitterUserAuthentication

object Config {

  def loadTwitterUserAuth[F[_]](s: String)(implicit F: Effect[F]): Stream[F, TwitterUserAuthentication] = Stream.eval(
    F.delay(loadConfig[TwitterUserAuthentication](s))
      .flatMap(validateOrError[F, TwitterUserAuthentication])
  )

  def validateOrError[F[_], A](e: Either[ConfigReaderFailures, A])(implicit F: Effect[F]): F[A] = e match {
    case Left(errors) =>
      F.raiseError(new Throwable(errors.toList.map(_.description).toString()))
    case Right(r) => F.pure(r)
  }
}

package tech.christopherdavenport.twitterstorm

import cats.effect.Sync
import cats.implicits._
import pureconfig.error.ConfigReaderFailures
import pureconfig.loadConfig
import tech.christopherdavenport.twitterstorm.authentication.TwitterUserAuthentication

object Config {

  def loadTwitterUserAuth[F[_]](s: String)(implicit F: Sync[F]): F[TwitterUserAuthentication] =
    F.delay(loadConfig[TwitterUserAuthentication](s))
      .flatMap(validateOrError[F, TwitterUserAuthentication])

  def validateOrError[F[_], A](e: Either[ConfigReaderFailures, A])(implicit F: Sync[F]): F[A] = e match {
    case Left(errors) =>
      F.raiseError(new Throwable(errors.toList.map(_.description).toString()))
    case Right(r) => F.pure(r)
  }


  trait ConfigService[F[_]]{
    def auth : F[TwitterUserAuthentication]
  }

  def impl[F[_]](implicit F: Sync[F]): ConfigService[F] = new ConfigService[F] {
    override def auth: F[TwitterUserAuthentication] = loadTwitterUserAuth[F]("twitterstorm")
  }
}

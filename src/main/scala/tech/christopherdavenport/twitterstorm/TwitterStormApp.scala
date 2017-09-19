package tech.christopherdavenport.twitterstorm

import cats.data.NonEmptyList
import cats.effect.IO
import fs2.Stream
import org.http4s.util.StreamApp

import scala.concurrent.ExecutionContext.Implicits.global

object TwitterStormApp extends StreamApp[IO] {

  def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, Nothing] = {
    val port = 8080
    val ip = "0.0.0.0"
//    val trackSmall = NonEmptyList.of("scala", "haskell", "dev", "programming", "development", "code")
    val trackLarge = NonEmptyList.of("uk", "british", "irish", "scottish",
      "us", "america", "american",
      "china", "chinese",
      "france", "french",
      "russia", "russian"
    )
    val topN = 100

    Config
      .loadTwitterUserAuth[IO]("twitterstorm")
      .through(Client.clientStream(trackLarge))
      .observeAsync(Int.MaxValue)(Server.serve(port, ip, topN))
      .drain
  }

}

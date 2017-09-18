package tech.christopherdavenport.twitterstorm

import cats.effect.IO
import fs2.Stream
import org.http4s.util.StreamApp

import scala.concurrent.ExecutionContext.Implicits.global

object TwitterStormApp extends StreamApp[IO] {

  def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, Nothing] = {
    val port = 8080
    val ip = "0.0.0.0"

    Config
      .loadTwitterUserAuth[IO]("twitterstorm")
      .through(Client.clientStream)
      .observeAsync(Int.MaxValue)(Server(_).serve(port, ip))
      .drain
  }

}

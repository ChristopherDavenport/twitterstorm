package tech.christopherdavenport.twitterstorm

import cats.effect.IO
import fs2.Stream
import org.http4s.util.StreamApp
import scala.concurrent.ExecutionContext.Implicits.global
import util._

object TwitterStormApp extends StreamApp[IO] {

  def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, Nothing] = {
    val port = 8080
    val ip = "0.0.0.0"

    Client.clientStream
      .observeAsync(1000)(t => Server[IO](t).server(port, ip)) // Creating the Server is a Side-Effect
      .drain
  }

}
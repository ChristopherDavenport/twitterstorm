package tech.christopherdavenport.twitterstorm

import cats.effect.IO
import fs2.Stream
import org.http4s.Request
import org.http4s.client.blaze.PooledHttp1Client
import org.http4s.util.StreamApp

import scala.concurrent.ExecutionContext.Implicits.global
import util._

object TwitterStormApp extends StreamApp[IO] {

  def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, Nothing] = {
    val port = 8080
    val ip = "0.0.0.0"

    for {
      config <- Config.loadTwitterUser[IO]
      result <- Client.clientStream[IO](config).observe(Server[IO](_).server(port, ip)).drain
    } yield result
  }

}

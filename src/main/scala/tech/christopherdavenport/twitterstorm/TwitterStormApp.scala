package tech.christopherdavenport.twitterstorm

import cats.effect.IO
import fs2.Stream
import org.http4s.util.StreamApp
import scala.concurrent.ExecutionContext.Implicits.global
import util._

object TwitterStormApp extends StreamApp[IO] {

  def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, Nothing] = {
    Client.clientStream.observe(printSink).drain
  }

}

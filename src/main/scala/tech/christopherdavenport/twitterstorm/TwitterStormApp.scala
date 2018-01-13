package tech.christopherdavenport.twitterstorm

import cats.data.NonEmptyList
import cats.effect.IO
import fs2.{Stream, StreamApp}
import org.http4s.client.blaze.Http1Client
import tech.christopherdavenport.twitterstorm.Client.Tracked

import scala.concurrent.ExecutionContext.Implicits.global

object TwitterStormApp extends StreamApp[IO] {

  def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, StreamApp.ExitCode] = {
    val port = 8080
    val ip = "0.0.0.0"
//    val trackSmall = NonEmptyList.of("scala", "haskell", "dev", "programming", "development", "code")
    val trackLarge = NonEmptyList.of("uk", "british", "irish", "scottish",
      "us", "america", "american",
      "china", "chinese",
      "france", "french",
      "russia", "russian"
    ).map(Tracked)

    // Top N Elements to Measure
    val topN = 100
    // Max Elements Queued into Queues.
    // Currently 3 Queues of Instant which is underlied by a Long
    val maxQueueSize = 100000
    // Emoji Resource File - In case you would like to provide your own.
    val emojiResource = "emoji.json"

    implicit val confService = Config.impl[IO]

    for {
      client <- Http1Client.stream[IO]()
      fireHose <- Stream.eval(Client.FireHose.impl[IO](client, IO.ioEffect, confService))
      exitCode <- Server(fireHose.spray(trackLarge)).serve(port, ip, topN, maxQueueSize, emojiResource)
    } yield exitCode
  }

}

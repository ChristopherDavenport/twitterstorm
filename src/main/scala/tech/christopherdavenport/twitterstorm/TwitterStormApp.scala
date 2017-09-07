package tech.christopherdavenport.twitterstorm

import cats.effect.IO
import fs2.Stream
import org.http4s.util.StreamApp
import scala.concurrent.ExecutionContext.Implicits.global
import util._

object TwitterStormApp extends StreamApp[IO] {

  def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, Nothing] = {
    val tweets = Client.clientStream //.observeAsync(1000)(printSink)
    val port = 8080
    val ip = "0.0.0.0"

    Server[IO](tweets).server(port, ip).concurrently(tweets)
  }

}


//  val totalTweets : Stream[F, Signal[F, BigInt]] = Stream.eval(fs2.async.signalOf[F, BigInt](0))
//
//  val increaseTotalTweets : Sink[F, BasicTweet] = _.flatMap(_ =>
//    totalTweets.flatMap(c => Stream.eval(c.modify(_ + 1))).map(_ => ())
//  )
//  val totalTweets : Stream[F, BigInt] =  {
//    val counter = fs2.async.signalOf[F, BigInt](0)
//    def increase1(signal: Signal[F, BigInt]) = signal.modify(_ + 1)
//    def increase1Sink(signal: Signal[F, BigInt]) : Sink[F, BasicTweet] = s => s.flatMap{ _ =>
//      Stream.eval(increase1(signal)).map(_ => ())
//    }
//
//    Stream.eval(counter).flatMap(signal =>
//      Stream.eval(tweets.observeAsync(100)(increase1Sink(signal)).run) >> signal.continuous
//    )
//  }
//  val counter = Stream.eval(fs2.async.signalOf[F, BigInt](0))
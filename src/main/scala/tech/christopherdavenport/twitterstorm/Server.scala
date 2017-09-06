package tech.christopherdavenport.twitterstorm

import cats.Semigroup
import cats.effect.Effect
import cats.implicits._
import org.http4s.HttpService
import org.http4s.dsl.Http4sDsl
import fs2.{Scheduler, Sink, Stream}
import fs2.async.mutable.Signal
import tech.christopherdavenport.twitterstorm.twitter.BasicTweet
import io.circe._
import org.http4s.server.blaze.BlazeBuilder
import org.http4s._
import org.http4s.circe._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import util._

case class Server[F[_]](tweets: Stream[F, BasicTweet])
                       (
                         implicit F : Effect[F],
                         S: Semigroup[F[MaybeResponse[F]]],
                         ec: ExecutionContext
                       ) extends Http4sDsl[F]{

  def service(
               counter: Signal[F, BigInt],
               timer: fs2.async.immutable.Signal[F, FiniteDuration]
             ) = HttpService[F] {
    case GET -> Root =>
      Ok("Server is Operational")
    case GET -> Root / "counter" =>
      val getAndIncrease = {
        counter.modify(_ + 1)
      }
      getAndIncrease.flatMap{
        i => Ok(Json.obj("total" -> Json.fromBigInt(i.now)))
      }
    case GET -> Root / "timer" =>
      timer.get.flatMap{ i =>
        Ok(Json.obj("timer" -> Json.fromLong(i.toMillis)))
      }
  }

  def twitterService(reporter: TweetReporter[F]): HttpService[F] = HttpService[F] {
    case GET -> Root / "total" =>
      reporter.totalTweets.flatMap{ i =>
        Ok(Json.obj("total" -> Json.fromBigInt(i)))
      }
  }

  def server(port: Int, ip: String) : Stream[F, Nothing] = {
    for {
      scheduler <- Scheduler[F](3)
      timer <- fs2.async.hold(Duration.Zero, scheduler.awakeEvery(10.millis))
      counter <- Stream.eval(fs2.async.signalOf[F, BigInt](0))
      reporter <- StreamTweetReporter(tweets, 100)
      serve <- BlazeBuilder[F]
          .bindHttp(port, ip)
          .mountService(service(counter, timer), "/util")
          .mountService(twitterService(reporter), "/twitter")
          .serve

    } yield serve
  }
}

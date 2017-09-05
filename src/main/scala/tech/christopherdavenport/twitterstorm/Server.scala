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

case class Server[F[_]](tweets: Stream[F, BasicTweet])
                       (
                         implicit F : Effect[F],
                         S: Semigroup[F[MaybeResponse[F]]],
                         ec: ExecutionContext
                       ) extends Http4sDsl[F]{

  def service(counter: Signal[F, BigInt], timer: fs2.async.immutable.Signal[F, FiniteDuration], tweetCounter: Signal[F, BigInt]) = HttpService[F] {
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
    case GET -> Root / "total" =>
      tweetCounter.get.flatMap{ i =>
        Ok(Json.obj("total" -> Json.fromBigInt(i)))
      }
  }

  def server(port: Int, ip: String) : Stream[F, Nothing] = {
//    val counter = Stream.eval(fs2.async.signalOf[F, BigInt](0))
//    val scheduler = Scheduler[F](3)
//
//    def timer(scheduler: Scheduler) = {
//      Stream.eval(fs2.async.signalOf[F, FiniteDuration](0.millis)).flatMap{ signal =>
//        val schedulerOp : Sink[F, Signal[F, FiniteDuration]] = _ => {
//          Stream.eval(scheduler.awakeEvery(10.millis).flatMap(t => Stream.eval(signal.set(t))).run)
//        }
//        Stream.emit(signal)
//          .observeAsync(1)(schedulerOp)
//          }
//    }
//
//    def tweetCounter = {
//      Stream.eval(fs2.async.signalOf[F, BigInt](0)).flatMap{ signal =>
//        val schedulerOp : Sink[F, Signal[F, BigInt]] = _.flatMap{_ =>
//          Stream.eval(tweets.flatMap(_ => Stream.eval(signal.modify(_ + 1)).map(_ => ())).run)
//        }
//        Stream.emit(signal)
//          .observe(schedulerOp)
//      }
//    }

    for {
      scheduler <- Scheduler[F](3)
      timer <- fs2.async.hold(Duration.Zero, scheduler.awakeEvery(10.millis))
      counter <- Stream.eval(fs2.async.signalOf[F, BigInt](0))
      tweetCounter <- Stream.eval(fs2.async.signalOf[F, BigInt](0)).flatMap{ signal =>
        val schedulerOp : Sink[F, Signal[F, BigInt]] = _.flatMap{_ =>
          Stream.eval(tweets.flatMap(_ => Stream.eval(signal.modify(_ + 1)).map(_ => ())).run)
        }
        Stream.emit(signal)
          .observe(schedulerOp)
      }
      serve <-  BlazeBuilder[F]
        .bindHttp(port, ip)
        .mountService(service(counter, timer, tweetCounter))
        .serve

    } yield serve

//    scheduler.flatMap{ scheduler =>
//      timer(scheduler).zip(counter).zip(tweetCounter).flatMap { case ((timeSignal, counterSignal), tweetCounter) =>
//        BlazeBuilder[F]
//          .bindHttp(port, ip)
//          .mountService(service(counterSignal, timeSignal, tweetCounter))
//          .serve
//      }
//    }
  }
}

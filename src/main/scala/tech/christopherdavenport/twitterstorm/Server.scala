package tech.christopherdavenport.twitterstorm

import cats.effect.Effect
import cats.implicits._
import org.http4s.HttpService
import org.http4s.dsl.Http4sDsl
import fs2.{Scheduler, Sink, Stream}
import tech.christopherdavenport.twitterstorm.twitter.BasicTweet
import org.http4s.server.blaze.BlazeBuilder
import io.circe.Json
import org.http4s.server.middleware.Logger
import org.http4s.circe._
import tech.christopherdavenport.twitterstorm.emoji.EmojiParser

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

case class Server[F[_]](tweets: Stream[F, BasicTweet])(
    implicit F: Effect[F],
    ec: ExecutionContext
) extends Http4sDsl[F] {

  def service(
      counter: fs2.async.mutable.Signal[F, BigInt],
      timer: fs2.async.immutable.Signal[F, FiniteDuration]
  ): HttpService[F] = HttpService[F] {
    case GET -> Root =>
      Ok("Server is Operational")
    case GET -> Root / "counter" =>
      val getAndIncrease = {
        counter.modify(_ + 1)
      }
      getAndIncrease.flatMap { i =>
        Ok(Json.obj("total" -> Json.fromBigInt(i.now)))
      }
    case GET -> Root / "timer" =>
      timer.get.flatMap { i =>
        Ok(Json.obj("timer" -> Json.fromLong(i.toMillis)))
      }
  }

  def twitterService(reporter: TweetReporter[F]): HttpService[F] =
    HttpService[F] {
      case GET -> Root / "total" / "tweets" =>
        reporter.totalTweets.flatMap { i =>
          Ok(Json.obj("tweets" -> Json.fromBigInt(i)))
        }
      case GET -> Root / "total" / "urls" =>
        reporter.totalUrls.flatMap { i =>
          Ok(Json.obj("urls" -> Json.fromBigInt(i)))
        }
      case GET -> Root / "total" / "pictures" =>
        reporter.totalPictureUrls.flatMap { i =>
          Ok(Json.obj("pictures" -> Json.fromBigInt(i)))
        }
      case GET -> Root / "total" / "hashtags" =>
        reporter.totalHashTags.flatMap { i =>
          Ok(Json.obj("hashtags" -> Json.fromBigInt(i)))
        }
      case GET -> Root / "total" / "emojis" =>
        reporter.totalEmojis.flatMap { i =>
          Ok(Json.obj("emojis" -> Json.fromBigInt(i)))
        }
      case GET -> Root / "total" / "emojiTweets" =>
        reporter.totalEmojiContainingTweets.flatMap { i =>
          Ok(Json.obj("emojiTweets" -> Json.fromBigInt(i)))
        }

      case GET -> Root / "percent" / "urls" =>
        reporter.percentUrls.flatMap {
          case (numer, denom) =>
            Ok(Json.obj("numerator" -> Json.fromBigInt(numer), "denominator" -> Json.fromBigInt(denom)))
        }
      case GET -> Root / "percent" / "pictures" =>
        reporter.percentPictureUrls.flatMap {
          case (numer, denom) =>
            Ok(Json.obj("numerator" -> Json.fromBigInt(numer), "denominator" -> Json.fromBigInt(denom)))
        }
      case GET -> Root / "percent" / "hashtags" =>
        reporter.percentHashtags.flatMap {
          case (numer, denom) =>
            Ok(Json.obj("numerator" -> Json.fromBigInt(numer), "denominator" -> Json.fromBigInt(denom)))
        }
      case GET -> Root / "percent" / "emojis" =>
        reporter.percentEmojiContaining.flatMap {
          case (numer, denom) =>
            Ok(Json.obj("numerator" -> Json.fromBigInt(numer), "denominator" -> Json.fromBigInt(denom)))
        }

      case GET -> Root / "average" / "second" =>
        reporter.tweetsPerSecond.flatMap(i => Ok(Json.obj("perSecond" -> Json.fromBigInt(i))))
      case GET -> Root / "average" / "minute" =>
        reporter.tweetsPerMinute.flatMap(i => Ok(Json.obj("perMinute" -> Json.fromBigInt(i))))
      case GET -> Root / "average" / "hour" =>
        reporter.tweetsPerHour.flatMap(i => Ok(Json.obj("perHour" -> Json.fromBigInt(i))))

      case GET -> Root / "top" / "hashtags" =>
        reporter.topHashtags.flatMap { hashtags =>
          val jsonValues = hashtags.map(Json.fromString)
          Ok(
            Json.obj("topHashtags" -> Json.fromValues(jsonValues))
          )
        }
      case GET -> Root / "top" / "domains" =>
        reporter.topDomains.flatMap { domains =>
          val jsonValues = domains.map(Json.fromString)
          Ok(
            Json.obj("topDomains" -> Json.fromValues(jsonValues))
          )
        }
      case GET -> Root / "top" / "emojis" =>
        reporter.topEmojis.flatMap { emojis =>
          val jsonValues = emojis.map(Json.fromString)
          Ok(
            Json.obj("topEmojis" -> Json.fromValues(jsonValues))
          )
        }
    }

  def serve(port: Int, ip: String, topN: Int, maxQueueSize: Int, emojiResource: String): Stream[F, Nothing] = {
    val logHeaders = true
    val logBody = true
    for {
      scheduler <- Scheduler[F](3)
      timer <- fs2.async.hold(Duration.Zero, scheduler.awakeEvery(10.millis))
      counter <- Stream.eval(fs2.async.signalOf[F, BigInt](0))
      emojiMap <- EmojiParser.emojiMapFromResource(emojiResource)
      reporter <- tweets.through(StreamTweetReporter(emojiMap, topN, maxQueueSize))
      nothing <- BlazeBuilder[F] // Stream[F, Nothing] I Would love to remove deadCode Warning Here.
        .bindHttp(port, ip)
        .mountService(service(counter, timer), "/util")
        .mountService(Logger(logHeaders, logBody)(twitterService(reporter)), "/twitter") // Logger for Console Visualization
        .serve
    } yield nothing
  }
}

object Server {

  def serve[F[_]](port: Int, ip: String, topN: Int, maxQueueSize: Int, emojiResource: String)
                 (implicit F: Effect[F], ec: ExecutionContext): Sink[F, BasicTweet] =
    Server(_).serve(port, ip, topN, maxQueueSize, emojiResource)

}

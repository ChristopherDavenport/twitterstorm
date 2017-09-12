package tech.christopherdavenport.twitterstorm

import cats.implicits._
import cats.effect.IO
import org.specs2._
import org.scalacheck._
import org.scalacheck.Arbitrary._
import tech.christopherdavenport.twitterstorm.twitter.BasicTweet
import fs2._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class StreamTweetReporterSpec extends Specification with ScalaCheck with ArbitraryInstances {

  val timeout: FiniteDuration = 60.seconds

  def runLogF[A](s: Stream[IO,A]): Future[Vector[A]] = (IO.shift >> s.runLog).unsafeToFuture

  def is = s2"""
                totalTweetCounter $countTotalTweets
    """

  def countTotalTweets = {
    prop{ (a: List[BasicTweet]) =>
      val stream = Stream.emits[BasicTweet](a)
      val f = StreamTweetReporter.totalTweetCounterSignal[IO](stream).concurrently(stream)

      val listSize : BigInt = BigInt(a.size)
      val finalSize : BigInt = f.runLast.unsafeRunSync().get.get.unsafeRunSync()

      finalSize must_=== listSize
    }
  }

}

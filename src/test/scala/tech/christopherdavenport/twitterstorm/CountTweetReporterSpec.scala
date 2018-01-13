package tech.christopherdavenport.twitterstorm

import cats.implicits._
import cats.effect.IO
import org.specs2._
import org.scalacheck._
import org.scalacheck.Arbitrary._
import tech.christopherdavenport.twitterstorm.twitter.BasicTweet
import fs2._
import tech.christopherdavenport.twitterstorm.StreamTweetReporter._

import scala.concurrent.ExecutionContext.Implicits.global

class CountTweetReporterSpec extends Specification with ScalaCheck with ArbitraryInstances {

  def is = s2"""
  count1 should succesfully Count the example list $count1
  countTotal Count N items That Pass Through   $countTotal
  totalTweetCounterSignal should count all tweets $countTweets
  totalHashTagCounterSignal should count all hashtags $countHashtags
  totalUrlCounterSignal should count all urls $countUrls
    """

  def count1 = {
    val a = List("1", "2", "3", "4", "5")
    val f: String => BigInt = _ => BigInt(1)

    val expected = a.map(f).fold(BigInt(0))(_ + _)
    val signalValue: Stream[IO, Option[BigInt]] = Stream
      .emits(a)
      .covary[IO]
      .through(countEach)
      .evalMap(s => IO(Thread.sleep(100)) >> IO(s))
      .evalMap(_.get)
      .last

    signalValue.compile.last.map(_.flatten).unsafeRunSync() should_=== Some(expected)
  }

  def countTotalSubset[A](p: Pipe[IO, A, fs2.async.immutable.Signal[IO, BigInt]], f: A => BigInt)(
      implicit arbitrary: Arbitrary[A]) = {
    prop { (a: List[A]) =>
      val expected = a.map(f).fold(BigInt(0))(_ + _)
      val signalValue: Stream[IO, Option[BigInt]] = Stream
        .emits(a)
        .covary[IO]
        .through(p)
        .evalMap(s => IO(Thread.sleep(50)) >> IO(s))
        .evalMap(_.get)
        .last

      signalValue.compile.last.map(_.flatten).unsafeRunSync() should_=== Some(expected)
    }
  }

  def countTotal = {
    prop { (a: List[String]) =>
      val listSize = BigInt(a.length)
      val signalValue: Stream[IO, Option[BigInt]] = Stream
        .emits(a)
        .covary[IO]
        .through(countEach[IO, String])
        .evalMap(s => IO(Thread.sleep(50)) >> IO(s))
        .evalMap(_.get)
        .last

      signalValue.compile.last.unsafeRunSync().flatten should_=== Some(listSize)
    }
  }

  def countTweets =
    countTotalSubset[BasicTweet](totalTweetCounterSignal, _ => BigInt(1))

  def countHashtags =
    countTotalSubset[BasicTweet](totalHashtagCounterSignal, _.entities.hashtags.length)

  def countUrls = countTotalSubset[BasicTweet](totalUrlCounterSignal, _.entities.urls.length)



}

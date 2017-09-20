package tech.christopherdavenport.twitterstorm

import java.time.ZonedDateTime

import cats.implicits._
import cats.effect.Effect
import com.twitter.algebird._
import fs2.{Pipe, Sink, Stream}
import fs2.async._
import fs2.async.mutable.Queue
import org.http4s.Uri.Host
import scodec.bits.ByteVector
import tech.christopherdavenport.twitterstorm.twitter.BasicTweet

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

object StreamTweetReporter {

  def totalCounter[F[_]: Effect, A](f: A => BigInt)(
      implicit ec: ExecutionContext): Pipe[F, A, immutable.Signal[F, BigInt]] = { stream =>
    hold(BigInt(0), stream.map(f).scan1(_ + _))
  }

  def countEach[F[_]: Effect, A](implicit ec: ExecutionContext): Pipe[F, A, immutable.Signal[F, BigInt]] =
    totalCounter(_ => BigInt(1))

  def totalTweetCounterSignal[F[_]: Effect](
      implicit ec: ExecutionContext): Pipe[F, BasicTweet, immutable.Signal[F, BigInt]] =
    countEach

  def totalUrlCounterSignal[F[_]: Effect](
      implicit ec: ExecutionContext): Pipe[F, BasicTweet, immutable.Signal[F, BigInt]] =
    totalCounter(_.entities.urls.size)

  def totalPictureUrlCounterSignal[F[_]: Effect](
      implicit ec: ExecutionContext): Pipe[F, BasicTweet, immutable.Signal[F, BigInt]] = {
    def containsNPictureUrls(b: BasicTweet): BigInt =
      b.entities.urls.count(url =>
          url.url.contains("pic.twitter") || url.url.contains("instagram") ||
          url.expanded_url.contains("pic.twitter") || url.expanded_url.contains("instagram")
      )
    totalCounter(containsNPictureUrls)
  }

  def totalHashtagCounterSignal[F[_]: Effect](
      implicit ec: ExecutionContext): Pipe[F, BasicTweet, immutable.Signal[F, BigInt]] =
    totalCounter(_.entities.hashtags.size)

  def totalEmojiContainingSignal[F[_]: Effect](emojis: Map[ByteVector, String])(
      implicit ec: ExecutionContext): Pipe[F, BasicTweet, immutable.Signal[F, BigInt]] = {
    def containsEmoji(b: BasicTweet): Boolean =
      emojis.keys.exists{k => ByteVector(b.text.getBytes).containsSlice(k)}
    def containsEmojiCount(b: BasicTweet): BigInt =
      if (containsEmoji(b)) BigInt(1) else BigInt(0)

    totalCounter(containsEmojiCount)
  }

  /**
   * Tweets is an infinite Stream. I am attempting to get the Average Tweets Per Unit Duration. However As I
   * Am Constantly removing and adding to the queue to filter, the result is extremely jumpy.
   * Looking to get a Smoother Indication of the Size of the Queue.
   */
  def averagePerDuration[F[_], A](finiteDuration: FiniteDuration, timeF: A => ZonedDateTime, maxSize: Int)(
      implicit F: Effect[F],
      ec: ExecutionContext): Pipe[F, A, immutable.Signal[F, Int]] =
    stream => {
      def generateCorrectQueueSize(queue: Queue[F, ZonedDateTime]): Sink[F,A] = stream => {
        val currentTimeToRemove = Stream.repeatEval[F, ZonedDateTime](F.delay(
            ZonedDateTime.now()
              .minusSeconds(finiteDuration.toSeconds)
              .minusSeconds(1)
        ))
        queue.dequeue
          .zip(currentTimeToRemove)
          .filter { case (tweetTime, zdt) => tweetTime.isAfter(zdt) }
          .map(_._1)
          .to(queue.enqueue)
          .concurrently(stream.map(timeF).to(queue.enqueue))
      }
      for {
        queue <- Stream.eval(fs2.async.circularBuffer[F, ZonedDateTime](maxSize))
        _ <- Stream(()).concurrently(stream.through(generateCorrectQueueSize(queue)))
      } yield {
        queue.size
      }
    }

  def averageTweetsPerSecond[F[_]](maxSize: Int)(
      implicit F: Effect[F],
      ec: ExecutionContext): Pipe[F, BasicTweet, immutable.Signal[F, Int]] =
    averagePerDuration(1.second, _.created_at, maxSize)

  def averageTweetsPerMinute[F[_]](maxSize: Int)(
      implicit F: Effect[F],
      ec: ExecutionContext): Pipe[F, BasicTweet, immutable.Signal[F, Int]] =
    averagePerDuration(1.minute, _.created_at, maxSize)

  def averageTweetsPerHour[F[_]](maxSize: Int)(
      implicit F: Effect[F],
      ec: ExecutionContext): Pipe[F, BasicTweet, fs2.async.immutable.Signal[F, Int]] =
    averagePerDuration(1.hour, _.created_at, maxSize)

  def topNBy[F[_], A](f: A => List[String], n: Int = 10)(
      implicit F: Effect[F],
      ec: ExecutionContext): Pipe[F, A, fs2.async.immutable.Signal[F, TopCMS[String]]] =
    tweets => {
    def topNCMSMonoid(n: Int): TopNCMSMonoid[String] = {
      val eps = 0.001
      val delta = 1E-10
      val seed = 2
      val topN = n
      TopNCMS.monoid(eps, delta, seed, topN)
    }
    val zero = topNCMSMonoid(n).zero
    hold(zero, tweets.flatMap(t => Stream.emits(f(t))).scan(zero)((cms, str) => cms + str))
  }

  def topNHashtags[F[_]](n: Int)
                        (implicit F: Effect[F], ec: ExecutionContext
                        ): Pipe[F, BasicTweet, fs2.async.immutable.Signal[F, TopCMS[String]]] =
    topNBy(_.entities.hashtags.map(_.text), n)

  def topNDomains[F[_]](n: Int)(
      implicit F: Effect[F],
      ec: ExecutionContext): Pipe[F, BasicTweet, fs2.async.immutable.Signal[F, TopCMS[String]]] = {
    def urls(tweet: BasicTweet): List[String] = {
      tweet.entities.urls
        .map(_.expanded_url)
        .map(org.http4s.Uri.fromString)
        .flatMap(_.fold(_ => List.empty, List(_)))
        .map(_.host)
        .flatMap(_.fold(List.empty[Host])(h => List(h)))
        .map(_.value)
    }
    topNBy(urls, n)
  }

  def topNEmojis[F[_]](n: Int, emojis: Map[ByteVector, String])(
      implicit F: Effect[F],
      ec: ExecutionContext): Pipe[F, BasicTweet, fs2.async.immutable.Signal[F, TopCMS[String]]] = {
    def emojiNames(tweet: BasicTweet): List[String] =
      emojis.toList.filter{ case (k, _) => ByteVector(tweet.text.getBytes).containsSlice(k) }.map(_._2)

    topNBy(emojiNames, n)
  }

  def apply[F[_]](emojiMap: Map[ByteVector, String], n : Int, maxQueueSize: Int)(implicit F: Effect[F], ec: ExecutionContext): Pipe[F, BasicTweet, TweetReporter[F]] = s => {
    for {
      totalSignal <- s.through(totalTweetCounterSignal)
      urlsSignal <- s.through(totalUrlCounterSignal)
      pictureUrlsSignal <- s.through(totalPictureUrlCounterSignal)
      hashtagSignal <- s.through(totalHashtagCounterSignal)
      emojiContainingSignal <- s.through(totalEmojiContainingSignal(emojiMap))

      avgTPS <- s.through(averageTweetsPerSecond(maxQueueSize))
      avgTPM <- s.through(averageTweetsPerMinute(maxQueueSize))
      avgTPH <- s.through(averageTweetsPerHour(maxQueueSize))

      topHTs <- s.through(topNHashtags(n))
      topDs <- s.through(topNDomains(n))
      topEmoji <- s.through(topNEmojis(n, emojiMap))

    } yield {
      new TweetReporter[F] {

        override def totalTweets: F[BigInt] = totalSignal.get

        override def totalUrls: F[BigInt] = urlsSignal.get

        override def totalPictureUrls: F[BigInt] = pictureUrlsSignal.get

        override def totalHashTags: F[BigInt] = hashtagSignal.get

        override def totalEmojis: F[BigInt] = topEmoji.get.map(_.totalCount).map(BigInt(_))

        override def totalEmojiContainingTweets: F[BigInt] = emojiContainingSignal.get

        override def tweetsPerHour: F[BigInt] = avgTPH.get.map(BigInt(_))

        override def tweetsPerMinute: F[BigInt] = avgTPM.get.map(BigInt(_))

        override def tweetsPerSecond: F[BigInt] = avgTPS.get.map(BigInt(_))

        override def topHashtags: F[List[String]] = topHTs.get.map(_.heavyHitters.toList)

        override def topDomains: F[List[String]] = topDs.get.map(_.heavyHitters.toList)

        override def topEmojis: F[List[String]] = topEmoji.get.map(_.heavyHitters.toList)

      }
    }
  }

}

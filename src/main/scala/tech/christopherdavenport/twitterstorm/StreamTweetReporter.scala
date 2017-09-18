package tech.christopherdavenport.twitterstorm

import java.time.ZonedDateTime

import cats.implicits._
import cats.effect.{Effect, IO}
import com.twitter.algebird._
import fs2.{Pipe, Scheduler, Sink, Stream}
import fs2.async._
import fs2.async.mutable.Queue
import org.http4s.Uri.Host
import org.log4s.getLogger
import tech.christopherdavenport.twitterstorm.emoji.EmojiParser
import tech.christopherdavenport.twitterstorm.twitter.BasicTweet

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import util._

object StreamTweetReporter {

  def totalCounter[F[_]: Effect, A](f: A => BigInt)(
      implicit ec: ExecutionContext): Pipe[F, A, immutable.Signal[F, BigInt]] = {
        stream =>  hold(BigInt(0), stream.map(f).scan1(_ + _))
//    def alterSignalSink(signal: fs2.async.mutable.Signal[F, BigInt]): Sink[F, A] =
//      _.evalMap{ t => signal.modify(_ + f(t)).void}
//
//    for {
//      signal <- Stream.eval(fs2.async.signalOf[F, BigInt](0))
//      result <- Stream.emit(signal).concurrently(stream.to(alterSignalSink(signal)))
//    } yield result
  }


  def countEach[F[_]: Effect, A](implicit ec: ExecutionContext): Pipe[F, A, immutable.Signal[F, BigInt]] =
    totalCounter(_ => BigInt(1))

  def totalTweetCounterSignal[F[_]: Effect](
      implicit ec: ExecutionContext): Pipe[F, BasicTweet, immutable.Signal[F, BigInt]] =
    _.observe(logSink(getLogger, TRACE, a => s"CountEach: ${a.toString}")).through(countEach)

  def totalUrlCounterSignal[F[_]: Effect](
      implicit ec: ExecutionContext): Pipe[F, BasicTweet, immutable.Signal[F, BigInt]] =
    _.observe(logSink(getLogger, TRACE, a => s"TotalUrlCounter: ${a.id}")).through(totalCounter(_.entities.urls.size))

  def totalPictureUrlCounterSignal[F[_]: Effect](
      implicit ec: ExecutionContext): Pipe[F, BasicTweet, immutable.Signal[F, BigInt]] = {
    def containsNPictureUrls(b: BasicTweet): BigInt = {
      b.entities.urls.count(
        url =>
          url.url.contains("pic.twitter") || url.url.contains("instagram") ||
            url.expanded_url.contains("pic.twitter") || url.expanded_url
            .contains("instagram"))
    }

    _.observe(logSink(getLogger, TRACE, a => s"TotalPictureURl: ${a.id}")).through(totalCounter(containsNPictureUrls))
  }

  def totalHashtagCounterSignal[F[_]: Effect](
      implicit ec: ExecutionContext): Pipe[F, BasicTweet, immutable.Signal[F, BigInt]] =
    _.observe(logSink(getLogger, TRACE, a => s"TotalHashtagCounter: ${a.id}")).through(totalCounter(_.entities.hashtags.size))

  def totalEmojiContainingSignal[F[_]: Effect](emojis: Map[Int, String])(
      implicit ec: ExecutionContext): Pipe[F, BasicTweet, immutable.Signal[F, BigInt]] = {
    def containsEmoji(b: BasicTweet): Boolean = {
      b.text.toList // Array Char
        .map(_.toInt)
        .map(emojis.get) // Option
        .exists(_.isDefined)
    }
    def containsEmojiCount(b: BasicTweet): BigInt =
      if (containsEmoji(b)) BigInt(1) else BigInt(0)

    _.observe(logSink(getLogger, TRACE, a => s"TotalEmojiCounter: ${a.id}")).through(totalCounter(containsEmojiCount))
  }

  /**
   * Tweets is an infinite Stream. I am attempting to get the Average Tweets Per Unit Duration. However As I
   * Am Constantly removing and adding to the queue to filter, the result is extremely jumpy.
   * Looking to get a Smoother Indication of the Size of the Queue.
   */
  def averageTweetsPerDuration[F[_]](finiteDuration: FiniteDuration)(
      implicit F: Effect[F],
      ec: ExecutionContext): Pipe[F, BasicTweet, immutable.Signal[F, Int]] =
    tweets => {
      def generateCorrectQueueSize(queue: Queue[F, BasicTweet]): Stream[F, Unit] = {
        val tweetsQueued = tweets.to(queue.enqueue)
        val currentTimeToRemove = Stream.repeatEval[F, ZonedDateTime](
          F.delay(
            ZonedDateTime
              .now()
              .minusSeconds(finiteDuration.toSeconds)
              .minusSeconds(1))
        )
        val remove = queue.dequeue
          .zip(currentTimeToRemove)
          .filter { case (bt, zdt) => bt.created_at.isAfter(zdt) }
          .map(_._1)
          .to(queue.enqueue)

        remove.concurrently(tweetsQueued)
      }

      for {
        queue <- Stream.eval(fs2.async.unboundedQueue[F, BasicTweet])
        _ <- Stream(()).concurrently(generateCorrectQueueSize(queue))
      } yield {
        queue.size
      }
    }

  def averageTweetsPerSecond[F[_]](
      implicit F: Effect[F],
      ec: ExecutionContext): Pipe[F, BasicTweet, immutable.Signal[F, Int]] =
    _.observe(logSink(getLogger, TRACE, a => s"AverageTweetsPerSecond: ${a.id}")).through(averageTweetsPerDuration(1.second))

  def averageTweetsPerMinute[F[_]](
      implicit F: Effect[F],
      ec: ExecutionContext): Pipe[F, BasicTweet, immutable.Signal[F, Int]] =
    _.observe(logSink(getLogger, TRACE, a => s"AverageTweetsPerMinute: ${a.id}")).through(averageTweetsPerDuration(1.minute))

  def averageTweetsPerHour[F[_]](
      implicit F: Effect[F],
      ec: ExecutionContext): Pipe[F, BasicTweet, fs2.async.immutable.Signal[F, Int]] =
    _.observe(logSink(getLogger, TRACE, a => s"AverageTweetsPerHour: ${a.id}")).through(averageTweetsPerDuration(1.hour))

  def topTweetsBy[F[_]](tweets: Stream[F, BasicTweet], f: BasicTweet => List[String])(
      implicit F: Effect[F],
      ec: ExecutionContext): Stream[F, fs2.async.immutable.Signal[F, TopCMS[String]]] = {
    def topNCMSMonoid: TopNCMSMonoid[String] = {
      val eps = 0.001
      val delta = 1E-10
      val seed = 1
      val topN = 10
//      val heavyHittersPct = 0.001
//      TopPctCMS.monoid[String](eps, delta, seed, heavyHittersPct)
      TopNCMS.monoid(eps, delta, seed, topN)
    }
    def addStream(t: TopCMS[String], elem: BasicTweet, f: BasicTweet => List[String]): TopCMS[String] =
      f(elem).foldLeft(t)((top, newE) => top + newE)
    val zero = topNCMSMonoid.create(Seq.empty)
    def adjustSignalBy(s: fs2.async.mutable.Signal[F, TopCMS[String]]): Sink[F, BasicTweet] =
      str => {
        for {
          tweet <- str
          res <- Stream.eval(s.modify(addStream(_, tweet, f)))
        } yield res
      }.drain

    for {
      signal <- Stream.eval(fs2.async.signalOf(zero))
      res <- Stream.emit(signal).concurrently(tweets.to(adjustSignalBy(signal)))
    } yield res

  }

  def topHashtags[F[_]](tweets: Stream[F, BasicTweet])(
      implicit F: Effect[F],
      ec: ExecutionContext): Stream[F, fs2.async.immutable.Signal[F, TopCMS[String]]] =
    topTweetsBy(tweets, _.entities.hashtags.map(_.text))

  def topDomains[F[_]](tweets: Stream[F, BasicTweet])(
      implicit F: Effect[F],
      ec: ExecutionContext): Stream[F, fs2.async.immutable.Signal[F, TopCMS[String]]] = {
    def urls(tweet: BasicTweet): List[String] = {
      tweet.entities.urls
        .map(_.expanded_url)
        .map(org.http4s.Uri.fromString)
        .flatMap(_.fold(_ => List.empty, List(_)))
        .map(_.host)
        .flatMap(_.fold(List.empty[Host])(h => List(h)))
        .map(_.value)
    }

    topTweetsBy(tweets, urls)
  }

  def topEmojis[F[_]](tweets: Stream[F, BasicTweet], emojis: Map[Int, String])(
      implicit F: Effect[F],
      ec: ExecutionContext): Stream[F, fs2.async.immutable.Signal[F, TopCMS[String]]] = {
    def emojiNames(tweet: BasicTweet): List[String] = {
      tweet.text.toList
        .map(_.toInt)
        .map(emojis.get)
        .flatMap {
          case Some(v) => List(v)
          case None => List.empty
        }
    }

    topTweetsBy(tweets, emojiNames)
  }

  // Naive Attempt to Build Queue with all tweets and buffer indefinitely.
  //      queue <- Stream.eval(fs2.async.unboundedQueue[F, BasicTweet]).flatMap{ q =>
  //        val queueOp: Sink[F, fs2.async.mutable.Queue[F, BasicTweet]] = _.flatMap{q =>
  //          Stream.eval(tweets.observe(q.enqueue)/**.observe(printSink)**/.run)
  //        }
  //        Stream.emit(q)
  //          .observe(queueOp)
  //      }

  def apply[F[_]](
      s: Stream[F, BasicTweet])(implicit F: Effect[F], ec: ExecutionContext): Stream[F, TweetReporter[F]] = {
    for {
      emojiMap <- EmojiParser.emojiMapFromFile
      totalSignal <- s.through(totalTweetCounterSignal)
      urlsSignal <- s.through(totalUrlCounterSignal)
      pictureUrlsSignal <- s.through(totalPictureUrlCounterSignal)
      hashtagSignal <- s.through(totalHashtagCounterSignal)
      emojiContainingSignal <- s.through(totalEmojiContainingSignal(emojiMap))
      avgTPS <- s.through(averageTweetsPerSecond)
      avgTPM <- s.through(averageTweetsPerMinute)
      avgTPH <- s.through(averageTweetsPerHour)

      topHTs <- topHashtags(s)
      topDs <- topDomains(s)
      topEmoji <- topEmojis(s, emojiMap)

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

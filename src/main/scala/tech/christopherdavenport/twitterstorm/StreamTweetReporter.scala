package tech.christopherdavenport.twitterstorm

import java.time.ZonedDateTime

import cats.implicits._
import cats.effect.Effect
import com.twitter.algebird._
import fs2.{Scheduler, Sink, Stream}
import fs2.async.mutable.{Queue, Signal}
import org.http4s.Uri.Host
import tech.christopherdavenport.twitterstorm.twitter.BasicTweet

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import util._


object StreamTweetReporter {

  def totalTweetsWithPredicate[F[_]](tweets: Stream[F, BasicTweet], p: BasicTweet => Boolean)
                                    (implicit F: Effect[F], ec: ExecutionContext): Stream[F, Signal[F, BigInt]] = {
    Stream.eval(fs2.async.signalOf[F, BigInt](0)).flatMap{ signal =>
      Stream.emit(signal)
        .concurrently(
          tweets.flatMap(t => if (p(t)) Stream.eval(signal.modify(_ + 1)).map(_ => ()) else Stream.empty)
        )
    }
  }

  def totalTweetCounterSignal[F[_]](tweets: Stream[F, BasicTweet])
                                   (implicit F: Effect[F], ec: ExecutionContext): Stream[F, Signal[F, BigInt]] = {
    totalTweetsWithPredicate(tweets, _ => true)
  }


  def totalUrlCounterSignal[F[_]](tweets: Stream[F, BasicTweet])
                                   (implicit F: Effect[F], ec: ExecutionContext): Stream[F, Signal[F, BigInt]] = {
    totalTweetsWithPredicate(tweets, _.entities.urls.nonEmpty)
  }

  def totalPictureUrlCounterSignal[F[_]](tweets: Stream[F, BasicTweet])
                                 (implicit F: Effect[F], ec: ExecutionContext): Stream[F, Signal[F, BigInt]] = {
    def containsPictureUrl(b: BasicTweet): Boolean = {
      b.entities.urls.exists(url =>
        url.url.contains("pic.twitter") || url.url.contains("instagram") ||
        url.expanded_url.contains("pic.twitter") || url.expanded_url.contains("instagram")
      )
    }

    totalTweetsWithPredicate(tweets, containsPictureUrl)
  }

  def totalHashtagCounterSignal[F[_]](tweets: Stream[F, BasicTweet])
                                 (implicit F: Effect[F], ec: ExecutionContext): Stream[F, Signal[F, BigInt]] = {
    totalTweetsWithPredicate(tweets, _.entities.hashtags.nonEmpty)
  }


  /**
    * Tweets is an infinite Stream. I am attempting to get the Average Tweets Per Unit Duration. However As I
    * Am Constantly removing and adding to the queue to filter, the result is extremely jumpy.
    * Looking to get a Smoother Indication of the Size of the Queue.
    */
  def averageTweetsPerDuration[F[_]](tweets: Stream[F, BasicTweet], finiteDuration: FiniteDuration)
                                  (implicit F: Effect[F], ec: ExecutionContext): Stream[F, fs2.async.immutable.Signal[F, Int]] = {
    def generateCorrectQueueSize(queue: Queue[F, BasicTweet]): Stream[F, Unit] = {
      val tweetsQueued = tweets.to(queue.enqueue)
      val currentTimeToRemove = Stream.repeatEval[F, ZonedDateTime](
        F.delay(ZonedDateTime.now().minusSeconds(finiteDuration.toSeconds).minusSeconds(1))
      )
      val remove = queue
        .dequeue
        .zip(currentTimeToRemove)
        .filter{ case (bt, zdt) => bt.created_at.isAfter(zdt)}
        .map(_._1)
        .to(queue.enqueue)

      remove.concurrently(tweetsQueued)
    }

    for {
      queue <- Stream.eval(fs2.async.unboundedQueue[F,BasicTweet])
      _ <- Stream(()).concurrently(generateCorrectQueueSize(queue))
    } yield {
      queue.size
    }
  }

  def averageTweetsPerSecond[F[_]](tweets: Stream[F, BasicTweet])
                               (implicit F: Effect[F], ec: ExecutionContext): Stream[F, fs2.async.immutable.Signal[F, Int]] = {
    averageTweetsPerDuration(tweets, 1.second)
  }

  def averageTweetsPerMinute[F[_]](tweets: Stream[F, BasicTweet])
                               (implicit F: Effect[F], ec: ExecutionContext): Stream[F, fs2.async.immutable.Signal[F, Int]] = {
    averageTweetsPerDuration(tweets, 1.minute)
  }

  def averageTweetsPerHour[F[_]](tweets: Stream[F, BasicTweet])
                               (implicit F: Effect[F], ec: ExecutionContext): Stream[F, fs2.async.immutable.Signal[F, Int]] = {
    averageTweetsPerDuration(tweets, 1.hour)
  }


  def topTweetsBy[F[_]](
                      tweets: Stream[F, BasicTweet],
                      f: BasicTweet => List[String]
                    )
                   (implicit F: Effect[F], ec: ExecutionContext): Stream[F, Signal[F, TopCMS[String]]] = {
    def topNCMSMonoid: TopNCMSMonoid[String] = {
      val eps = 0.001
      val delta = 1E-10
      val seed = 1
      val topN = 10
//      val heavyHittersPct = 0.001
//      TopPctCMS.monoid[String](eps, delta, seed, heavyHittersPct)
      TopNCMS.monoid(eps, delta, seed, topN)
    }
    def addStream(t: TopCMS[String], elem: BasicTweet, f: BasicTweet => List[String]): TopCMS[String] = {
      f(elem).foldLeft(t)((top, newE) =>  top + newE)
    }
    val zero = topNCMSMonoid.create(Seq.empty)
    def adjustSignalBy(s: Signal[F, TopCMS[String]]): Sink[F, BasicTweet] =
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

  def topHashtags[F[_]](tweets: Stream[F, BasicTweet])
                    (implicit F: Effect[F], ec: ExecutionContext): Stream[F, fs2.async.immutable.Signal[F, TopCMS[String]]] = {
    topTweetsBy(tweets, _.entities.hashtags.map(_.text))
  }

  def topDomains[F[_]](tweets: Stream[F, BasicTweet])
                   (implicit F: Effect[F], ec: ExecutionContext): Stream[F, fs2.async.immutable.Signal[F, TopCMS[String]]] = {
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



  // Naive Attempt to Build Queue with all tweets and buffer indefinitely.
  //      queue <- Stream.eval(fs2.async.unboundedQueue[F, BasicTweet]).flatMap{ q =>
  //        val queueOp: Sink[F, fs2.async.mutable.Queue[F, BasicTweet]] = _.flatMap{q =>
  //          Stream.eval(tweets.observe(q.enqueue)/**.observe(printSink)**/.run)
  //        }
  //        Stream.emit(q)
  //          .observe(queueOp)
  //      }




  def apply[F[_]](s: Stream[F, BasicTweet], scheduler: Scheduler)
                 (implicit F: Effect[F], ec: ExecutionContext): Stream[F, TweetReporter[F]] = {
    for {
      totalSignal <- totalTweetCounterSignal(s)
      urlsSignal <- totalUrlCounterSignal(s)
      pictureUrlsSignal <- totalPictureUrlCounterSignal(s)
      hashtagSignal <- totalHashtagCounterSignal(s)
      avgTPS <- averageTweetsPerSecond(s)
      avgTPM <- averageTweetsPerMinute(s)
      avgTPH <- averageTweetsPerHour(s)

      topHTs <- topHashtags(s)
      topDs <- topDomains(s)

    } yield {
      new TweetReporter[F] {

        override def totalTweets: F[BigInt] = totalSignal.get

        override def totalUrls: F[BigInt] = urlsSignal.get

        override def totalPictureUrls: F[BigInt] = pictureUrlsSignal.get

        override def totalHashTags: F[BigInt] = topHTs.get.map(_.totalCount).map(BigInt(_))

        override def tweetsPerHour: F[BigInt] = avgTPH.get.map(BigInt(_))

        override def tweetsPerMinute: F[BigInt] = avgTPM.get.map(BigInt(_))

        override def tweetsPerSecond: F[BigInt] = avgTPS.get.map(BigInt(_))

        override def topHashtags: F[List[String]] = topHTs.get.map(_.heavyHitters.toList)

        override def topDomains: F[List[String]] = topDs.get.map(_.heavyHitters.toList)

      }
    }
  }

}

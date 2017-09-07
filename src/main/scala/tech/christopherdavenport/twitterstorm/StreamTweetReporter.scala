package tech.christopherdavenport.twitterstorm

import java.time.ZonedDateTime

import cats.implicits._
import cats.effect.Effect
import fs2.{Scheduler, Sink, Stream}
import fs2.async.mutable.{Queue, Signal}
import tech.christopherdavenport.twitterstorm.twitter.BasicTweet

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import util._


object StreamTweetReporter {

  def totalTweetsWithPredicate[F[_]](tweets: Stream[F, BasicTweet], p: BasicTweet => Boolean, waitSize: Int)
                                    (implicit F: Effect[F], ec: ExecutionContext): Stream[F, Signal[F, BigInt]] = {
    Stream.eval(fs2.async.signalOf[F, BigInt](0)).flatMap{ signal =>
      Stream.emit(signal)
        .concurrently(
          tweets.flatMap(t => if (p(t)) Stream.eval(signal.modify(_ + 1)).map(_ => ()) else Stream.empty)
        )
    }
  }

  def totalTweetCounterSignal[F[_]](tweets: Stream[F, BasicTweet], waitSize: Int)
                                   (implicit F: Effect[F], ec: ExecutionContext): Stream[F, Signal[F, BigInt]] = {
    totalTweetsWithPredicate(tweets, _ => true, waitSize)
  }


  def totatUrlCounterSignal[F[_]](tweets: Stream[F, BasicTweet], waitSize: Int)
                                   (implicit F: Effect[F], ec: ExecutionContext): Stream[F, Signal[F, BigInt]] = {
    totalTweetsWithPredicate(tweets, _.entities.urls.nonEmpty , waitSize)
  }

  def totatPictureUrlCounterSignal[F[_]](tweets: Stream[F, BasicTweet], waitSize: Int)
                                 (implicit F: Effect[F], ec: ExecutionContext): Stream[F, Signal[F, BigInt]] = {
    def containsPictureUrl(b: BasicTweet): Boolean = {
      b.entities.urls.exists(url =>
        url.url.contains("pic.twitter") || url.url.contains("instagram") ||
        url.expanded_url.contains("pic.twitter") || url.expanded_url.contains("instagram")
      )
    }

    totalTweetsWithPredicate(tweets, containsPictureUrl , waitSize)
  }


  /**
    * Tweets is an infinite Stream. I am attempting to get the Average Tweets Per Unit Duration. However As I
    * Am Constantly removing and adding to the queue to filter, the result is extremely jumpy.
    * Looking to get a Smoother Indication of the Size of the Queue.
    */
  def averageTweetsPerDuration[F[_]](tweets: Stream[F, BasicTweet], scheduler: Scheduler, finiteDuration: FiniteDuration, waitSize: Int)
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
          .observeAsync(waitSize)(_.map{case (bt, zdt) => (bt.created_at, zdt)}.to(printSink))
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

  def averageTweetsPerSecond[F[_]](tweets: Stream[F, BasicTweet], scheduler: Scheduler)
                               (implicit F: Effect[F], ec: ExecutionContext): Stream[F, fs2.async.immutable.Signal[F, Int]] = {
    averageTweetsPerDuration(tweets, scheduler, 1.second, 3)
  }

  def averageTweetsPerMinute[F[_]](tweets: Stream[F, BasicTweet], scheduler: Scheduler)
                               (implicit F: Effect[F], ec: ExecutionContext): Stream[F, fs2.async.immutable.Signal[F, Int]] = {
    averageTweetsPerDuration(tweets, scheduler, 1.minute, 3)
  }

  def averageTweetsPerHour[F[_]](tweets: Stream[F, BasicTweet], scheduler: Scheduler)
                               (implicit F: Effect[F], ec: ExecutionContext): Stream[F, fs2.async.immutable.Signal[F, Int]] = {
    averageTweetsPerDuration(tweets, scheduler, 1.hour, 3)
  }

  // Naive Attempt to Build Queue with all tweets and buffer indefinitely.
  //      queue <- Stream.eval(fs2.async.unboundedQueue[F, BasicTweet]).flatMap{ q =>
  //        val queueOp: Sink[F, fs2.async.mutable.Queue[F, BasicTweet]] = _.flatMap{q =>
  //          Stream.eval(tweets.observe(q.enqueue)/**.observe(printSink)**/.run)
  //        }
  //        Stream.emit(q)
  //          .observe(queueOp)
  //      }




  def apply[F[_]](s: Stream[F, BasicTweet], scheduler: Scheduler, waitSize: Int)
                 (implicit F: Effect[F], ec: ExecutionContext): Stream[F, TweetReporter[F]] = {
    for {
      totalSignal <- totalTweetCounterSignal(s, waitSize)
      urlsSignal <- totatUrlCounterSignal(s, waitSize)
      pictureUrlsSignal <- totatPictureUrlCounterSignal(s, waitSize)
      avgTPS <- averageTweetsPerSecond(s, scheduler)
      avgTPM <- averageTweetsPerMinute(s, scheduler)
      avgTPH <- averageTweetsPerHour(s, scheduler)

    } yield {
      new TweetReporter[F] {

        override def totalTweets: F[BigInt] = totalSignal.get

        override def totalUrls: F[BigInt] = urlsSignal.get

        override def totalPictureUrls: F[BigInt] = pictureUrlsSignal.get

        override def tweetsPerHour: F[BigInt] = avgTPH.get.map(BigInt(_))

        override def tweetsPerMinute: F[BigInt] = avgTPM.get.map(BigInt(_))

        override def tweetsPerSecond: F[BigInt] = avgTPS.get.map(BigInt(_))


      }
    }
  }

}

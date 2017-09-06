package tech.christopherdavenport.twitterstorm

import cats.effect.Effect
import fs2.{Sink, Stream}
import fs2.async.mutable.Signal
import tech.christopherdavenport.twitterstorm.twitter.BasicTweet

import scala.concurrent.ExecutionContext


object StreamTweetReporter {

  def totalTweetsWithPredicate[F[_]](tweets: Stream[F, BasicTweet], p: BasicTweet => Boolean, waitSize: Int)
                                    (implicit F: Effect[F], ec: ExecutionContext): Stream[F, Signal[F, BigInt]] = {
    Stream.eval(fs2.async.signalOf[F, BigInt](0)).flatMap{ signal =>
      val scheduledOp : Sink[F, Signal[F, BigInt]] = _.flatMap{_ =>
        Stream.eval(
          tweets.flatMap(t =>
            if (p(t)) Stream.eval(signal.modify(_ + 1)).map(_ => ()) else Stream.empty
          ).run
        )
      }
      Stream.emit(signal)
        .observeAsync(waitSize)(scheduledOp)
    }
  }

  def totalTweetCounterSignal[F[_]](tweets: Stream[F, BasicTweet], waitSize: Int)
                                   (implicit F: Effect[F], ec: ExecutionContext): Stream[F, Signal[F, BigInt]] = {
    totalTweetsWithPredicate(tweets, _ => true, waitSize)
  }


  // Naive Attempt to Build Queue with all tweets and buffer indefinitely.
  //      queue <- Stream.eval(fs2.async.unboundedQueue[F, BasicTweet]).flatMap{ q =>
  //        val queueOp: Sink[F, fs2.async.mutable.Queue[F, BasicTweet]] = _.flatMap{q =>
  //          Stream.eval(tweets.observe(q.enqueue)/**.observe(printSink)**/.run)
  //        }
  //        Stream.emit(q)
  //          .observe(queueOp)
  //      }




  def apply[F[_]](s: Stream[F, BasicTweet], waitSize: Int)(implicit F: Effect[F], ec: ExecutionContext): Stream[F, TweetReporter[F]] = {
    for {
      totalSignal <- totalTweetCounterSignal(s, waitSize)

    } yield {
      new TweetReporter[F] {

        override def totalTweets: F[BigInt] = totalSignal.get

      }
    }
  }

}

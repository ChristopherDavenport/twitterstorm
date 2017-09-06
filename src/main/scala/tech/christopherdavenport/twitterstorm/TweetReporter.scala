package tech.christopherdavenport.twitterstorm

import cats.implicits._
import cats.effect.Effect
import tech.christopherdavenport.twitterstorm.twitter.{BasicTweet, Hashtag}
import fs2.{Sink, Stream}
import fs2.async.mutable.Signal

abstract class TweetReporter[F[_]](implicit F: Effect[F]){

  def totalTweets: F[BigInt]

  def totalUrls: F[BigInt]

  def totalPictureUrls: F[BigInt]

//  def totalEmojiContainingTweets: F[BigInt]

//  def totalEmojis: F[BigInt]

  def percentUrls: F[(BigInt, BigInt)] = for {
    tweets <- totalTweets
    urls <- totalUrls
  } yield {
    (urls, tweets)
  }

  def percentPictureUrls: F[(BigInt, BigInt)] = for {
    tweets <- totalTweets
    pictureUrls <- totalPictureUrls
  } yield {
    (pictureUrls, tweets)
  }
//
//  def percentEmojiContaining: F[(BigInt, BigInt)] = for {
//    tweets <- totalTweets
//    emojiTweets <- totalEmojiContainingTweets
//  } yield {
//    (emojiTweets, tweets)
//  }
//
//  def topHashtags: F[List[Hashtag]]
//
//  def topDomains: F[List[String]]
//
//  def topEmojis: F[List[String]]
//
//  def tweetsPerSecond: F[BigInt]
//
//  def tweetsPerMinute: F[BigInt]
//
//  def tweetsPerHour: F[BigInt]

}

object TweetReporter {


}

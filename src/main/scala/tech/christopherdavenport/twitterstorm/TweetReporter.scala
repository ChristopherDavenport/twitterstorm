package tech.christopherdavenport.twitterstorm

import cats.implicits._
import cats.effect.Effect

abstract class TweetReporter[F[_]](implicit F: Effect[F]) {

  def totalTweets: F[BigInt]

  def totalUrls: F[BigInt]

  def totalPictureUrls: F[BigInt]

  def totalHashTags: F[BigInt]

  def totalEmojiContainingTweets: F[BigInt]

  def totalEmojis: F[BigInt]

  def percentHashtags: F[(BigInt, BigInt)] =
    for {
      tweets <- totalTweets
      hashtags <- totalHashTags
    } yield (hashtags, tweets)

  def percentUrls: F[(BigInt, BigInt)] =
    for {
      tweets <- totalTweets
      urls <- totalUrls
    } yield {
      (urls, tweets)
    }

  def percentPictureUrls: F[(BigInt, BigInt)] =
    for {
      tweets <- totalTweets
      pictureUrls <- totalPictureUrls
    } yield {
      (pictureUrls, tweets)
    }

  def percentEmojiContaining: F[(BigInt, BigInt)] =
    for {
      tweets <- totalTweets
      emojiTweets <- totalEmojiContainingTweets
    } yield {
      (emojiTweets, tweets)
    }

  def topHashtags: F[List[String]]

  def topDomains: F[List[String]]

  def topEmojis: F[List[String]]

  def tweetsPerSecond: F[BigInt]

  def tweetsPerMinute: F[BigInt]

  def tweetsPerHour: F[BigInt]

}

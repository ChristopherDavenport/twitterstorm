package tech.christopherdavenport.twitterstorm.twitter

import io.circe.{Decoder, Encoder, HCursor, Json}
import cats.{Eq, Show}
import java.time._
import java.time.format.DateTimeFormatter

import scala.util.Try

case class BasicTweet(
    created_at: ZonedDateTime,
    id: BigInt,
    text: String,
    entities: Entities,
    quote_count: BigInt,
    reply_count: BigInt,
    retweet_count: BigInt,
    favorite_count: BigInt
)

object BasicTweet {

  implicit val basicTweetDecoder: Decoder[BasicTweet] =
    new Decoder[BasicTweet] {

      final def apply(c: HCursor): Decoder.Result[BasicTweet] = {
        for {
          created <- c.downField("created_at").as[ZonedDateTime]
          id <- c.downField("id").as[BigInt]
          text <- c.downField("text").as[String]
          entities <- c.downField("entities").as[Entities]
          quoted <- c.downField("quote_count").as[BigInt]
          replied <- c.downField("reply_count").as[BigInt]
          retweeted <- c.downField("retweet_count").as[BigInt]
          favorited <- c.downField("favorite_count").as[BigInt]

        } yield {
          BasicTweet(created, id, text, entities, quoted, replied, retweeted, favorited)
        }
      }

    }

  implicit val ZonedDateTimeFormat: Encoder[ZonedDateTime] with Decoder[ZonedDateTime] =
    new Encoder[ZonedDateTime] with Decoder[ZonedDateTime] {
      override def apply(a: ZonedDateTime): Json =
        Encoder.encodeString(a.toString)

      override def apply(c: HCursor): Decoder.Result[ZonedDateTime] =
        Decoder.decodeString.map(str =>
          ZonedDateTime.parse(str, DateTimeFormatter.ofPattern("EE MMM dd HH:mm:ss xxxx uuuu")))(c)
    }

  implicit val basicTweetEq: Eq[BasicTweet] = Eq.fromUniversalEquals[BasicTweet]
  implicit val basicTweetShow: Show[BasicTweet] = Show.fromToString[BasicTweet]

}

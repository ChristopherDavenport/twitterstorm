package tech.christopherdavenport.twitterstorm.twitter

import io.circe.{Decoder, HCursor}
import cats.{Eq, Show}

case class BasicTweet(
                     created_at: String,
                     id: BigInt,
                     text: String,
                     entities: Entities,
                     quote_count: BigInt,
                     reply_count: BigInt,
                     retweet_count: BigInt,
                     favorite_count: BigInt
                     )

object BasicTweet {

  implicit val basicTweetDecoder : Decoder[BasicTweet] = new Decoder[BasicTweet]{

    final def apply(c: HCursor): Decoder.Result[BasicTweet] = {
      for {
        created <- c.downField("created_at").as[String]
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

  implicit val basicTweetEq : Eq[BasicTweet] = Eq.fromUniversalEquals[BasicTweet]
  implicit val basicTweetShow : Show[BasicTweet] = Show.fromToString[BasicTweet]

}

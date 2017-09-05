package tech.christopherdavenport.twitterstorm.twitter

import io.circe.{Decoder, HCursor}
import cats.{Eq, Show}

case class BasicTweet(
                     text: String,
                     entities: Entities
                     )

object BasicTweet {

  implicit val basicTweetDecoder : Decoder[BasicTweet] = new Decoder[BasicTweet]{

    final def apply(c: HCursor): Decoder.Result[BasicTweet] = {
      for {
        text <- c.downField("text").as[String]
        entities <- c.downField("entities").as[Entities]

      } yield {
        BasicTweet(text, entities)
      }
    }

  }

  implicit val basicTweetEq : Eq[BasicTweet] = Eq.fromUniversalEquals[BasicTweet]
  implicit val basicTweetShow : Show[BasicTweet] = Show.fromToString[BasicTweet]

}



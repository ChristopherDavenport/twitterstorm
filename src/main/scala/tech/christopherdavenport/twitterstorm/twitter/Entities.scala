package tech.christopherdavenport.twitterstorm.twitter

import io.circe.generic.JsonCodec
import cats.Eq

@JsonCodec
case class Entities(
    hashtags: List[Hashtag],
    urls: List[Url],
    user_mentions: List[UserMention],
    symbols: List[Symbol],
    media: Option[List[Media]]
)

object Entities {

  implicit val entitiesEq: Eq[Entities] = Eq.fromUniversalEquals[Entities]

}

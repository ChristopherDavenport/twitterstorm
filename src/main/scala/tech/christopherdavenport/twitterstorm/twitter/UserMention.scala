package tech.christopherdavenport.twitterstorm.twitter

import io.circe.generic.JsonCodec
import cats.Eq

@JsonCodec
case class UserMention(
    id: BigInt,
    id_str: String,
    screen_name: String,
    name: String,
    indices: List[Int]
)

object UserMention {
  implicit val userMentionEq: Eq[UserMention] =
    Eq.fromUniversalEquals[UserMention]

}

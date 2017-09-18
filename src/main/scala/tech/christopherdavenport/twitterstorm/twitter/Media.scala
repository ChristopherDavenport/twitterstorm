package tech.christopherdavenport.twitterstorm.twitter

import cats.Eq
import io.circe.generic.JsonCodec

@JsonCodec
case class Media(
    id: Option[BigInt],
    id_str: Option[String],
    media_url: Option[String],
    media_url_https: Option[String],
    url: Option[String],
    display_url: Option[String],
    expanded_url: Option[String],
    sizes: Option[Sizes],
    `type`: Option[String],
    indices: Option[List[Int]]
)

object Media {
  implicit val entitiesEq: Eq[Media] = Eq.fromUniversalEquals[Media]

}

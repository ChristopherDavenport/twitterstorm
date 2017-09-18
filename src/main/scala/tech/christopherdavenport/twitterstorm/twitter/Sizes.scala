package tech.christopherdavenport.twitterstorm.twitter

import io.circe.generic.JsonCodec

@JsonCodec
case class Sizes(
    medium: Option[Size],
    thumb: Option[Size],
    small: Option[Size],
    large: Option[Size]
)

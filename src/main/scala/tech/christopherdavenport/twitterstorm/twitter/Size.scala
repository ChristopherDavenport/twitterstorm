package tech.christopherdavenport.twitterstorm.twitter

import io.circe.generic.JsonCodec

@JsonCodec
case class Size(
    w: Int,
    h: Int,
    resize: String
)

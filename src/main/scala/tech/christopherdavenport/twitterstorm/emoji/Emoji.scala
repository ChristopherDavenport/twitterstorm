package tech.christopherdavenport.twitterstorm.emoji

import _root_.io.circe.generic.JsonCodec

@JsonCodec
case class Emoji(
    name: Option[String],
    unified: String,
    has_img_twitter: Boolean
)

package tech.christopherdavenport.twitterstorm.authentication

import io.circe.generic.JsonCodec

@JsonCodec
case class TwitterOauthResponse(
    token_type: String,
    access_token: String
)

package tech.christopherdavenport.twitterstorm.authentication

case class TwitterUserAuthentication(
    consumerKey: String,
    consumerSecret: String,
    userKey: String,
    userSecret: String
)

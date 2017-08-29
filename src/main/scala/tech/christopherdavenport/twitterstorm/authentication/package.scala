package tech.christopherdavenport.twitterstorm

import cats.effect.{Effect, Sync}
import org.http4s.Request
import org.http4s.client.oauth1.{Consumer, Token, signRequest}

package object authentication {

  def userSign[F[_]: Effect](
                              consumerKey: String,
                              consumerSecret: String,
                              accessKey: String,
                              accessToken: String
                            )
                            (req: Request[F]): F[Request[F]]  = {

    val accessCredentialsToken = Token(accessKey, accessToken)
    val consumer = Consumer(consumerKey, consumerSecret)

    signRequest(req, consumer, None, None, Option(accessCredentialsToken))
  }


  //  val oauthRequest : Request[IO] = Request[IO](
  //    POST,
  //    Uri.unsafeFromString("https://api.twitter.com/oauth2/token"),
  //    headers = Headers(
  ////      Authorization(BasicCredentials("appKey", "appSecret")),
  //      `Content-Type`(MediaType.`application/x-www-form-urlencoded`, Charset.`UTF-8`)
  //    ),
  //    body = Stream("grant_type=client_credentials").through(fs2.text.utf8Encode)
  //  )
}

package tech.christopherdavenport.twitterstorm

import cats.effect.Effect
import cats.implicits._
import fs2._
import org.http4s._
import org.http4s.headers._
import org.http4s.Method.POST
import org.http4s.client.oauth1.{signRequest, Consumer, Token}
import org.http4s.client.{Client => HClient}
import org.http4s.circe._

package object authentication {

  def userSign[F[_]: Effect](
      consumerKey: String,
      consumerSecret: String,
      accessKey: String,
      accessToken: String
  )(req: Request[F]): F[Request[F]] = {

    val accessCredentialsToken = Token(accessKey, accessToken)
    val consumer = Consumer(consumerKey, consumerSecret)

    signRequest(req, consumer, None, None, Option(accessCredentialsToken))
  }

  def userSign[F[_]](ta: TwitterUserAuthentication)(implicit F: Effect[F]): Pipe[F, Request[F], Request[F]] =
    _.evalMap { userSign[F](ta.consumerKey, ta.consumerSecret, ta.userKey, ta.userSecret) }

  def oauthRequest[F[_]: Effect](appKey: String, appSecret: String): Request[F] =
    Request[F](
      POST,
      Uri.unsafeFromString("https://api.twitter.com/oauth2/token"),
      headers = Headers(
        Authorization(BasicCredentials(appKey, appSecret)),
        `Content-Type`(MediaType.`application/x-www-form-urlencoded`, Charset.`UTF-8`)
      ),
      body = Stream("grant_type=client_credentials").through(fs2.text.utf8Encode)
    )

  def oauthGenerateHeader[F[_]](ta: TwitterOauthAuthentication, client: HClient[F])(
      implicit F: Effect[F]): F[Header] = {
    for {
      tor <- client.expect(oauthRequest(ta.appKey, ta.appSecret))(jsonOf[F, TwitterOauthResponse])
    } yield Authorization(Credentials.Token(AuthScheme.Bearer, tor.access_token))
  }

  def oauthSign[F[_]](ta: TwitterOauthAuthentication, client: HClient[F])(
      implicit F: Effect[F]): Pipe[F, Request[F], Request[F]] = requests => {
    for {
      authHeader <- Stream.eval(oauthGenerateHeader(ta, client)) // Generate Header Once
      request <- requests
    } yield request.withHeaders(request.headers.put(authHeader)) // For Each Request Sign with Authorization
  }

}

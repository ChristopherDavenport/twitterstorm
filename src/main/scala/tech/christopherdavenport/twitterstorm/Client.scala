package tech.christopherdavenport.twitterstorm

import cats.data.NonEmptyList
import cats.effect.Effect
import cats.implicits._
import fs2.{Pipe, Segment, Stream}
import io.circe.{Json, Printer}
import io.circe.fs2.byteStreamParserS
import org.http4s.{Headers, MediaType, Request, Uri}
import org.http4s.client.blaze.PooledHttp1Client
import org.http4s.dsl.io.POST
import org.http4s.headers.`Content-Type`
import tech.christopherdavenport.twitterstorm.authentication._
import tech.christopherdavenport.twitterstorm.twitter.BasicTweet
import tech.christopherdavenport.twitterstorm.util._

object Client {

  /**
   * This stream takes an authentication and generates a Stream of Tweets from that authentication
   * @param track NonEmptyList of Strings to Request to Track, Must have a parameter so NonEmpty is required.
   * @return Infinite Stream of Tweets
   */
  def clientStream[F[_]](track: NonEmptyList[String])(implicit F: Effect[F]): Pipe[F, TwitterUserAuthentication, BasicTweet] =
    clientBodyStream(track) andThen byteStreamParserS andThen tweetPipeS andThen filterLeft

  def twitterStreamRequest[F[_]: Effect](track: NonEmptyList[String]): Request[F] = Request[F](
    POST,
//    Uri.unsafeFromString("https://stream.twitter.com/1.1/statuses/sample.json"),
    Uri.unsafeFromString("https://stream.twitter.com/1.1/statuses/filter.json")
      .withQueryParam("track", track.toList),
    headers = Headers(`Content-Type`(MediaType.`application/x-www-form-urlencoded`))
  )

  //Cleaner Implementation, Be Careful FlatMapping on client.streaming with just the body as that runs each Byte
  // and loses fusion. Transform to Segments for performant code.
  def clientBodyStream[F[_]](track: NonEmptyList[String])(implicit F: Effect[F]): Pipe[F, TwitterUserAuthentication, Segment[Byte, Unit]] =
    taS =>
      for {
        ta <- taS
        client <- Stream.emit(PooledHttp1Client(1))
        signedRequest <- Stream.repeatEval(F.pure(twitterStreamRequest(track))) // Endlessly Generate Requests
          .through(userSign(ta)) // Sign Them
        infiniteEntityBody <- client.streaming(signedRequest)(_.body.segments) // Transform to Efficient Segments
      } yield infiniteEntityBody

  def tweetPipeS[F[_]]: Pipe[F, Json, Either[String, BasicTweet]] = _.map{ json =>
    json.as[BasicTweet].leftMap(pE => s"ParseError: ${pE.message} - ${json.pretty(Printer.noSpaces)}")
  }

}

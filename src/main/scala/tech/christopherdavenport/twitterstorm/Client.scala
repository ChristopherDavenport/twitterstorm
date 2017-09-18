package tech.christopherdavenport.twitterstorm

import cats.effect.Effect
import cats.implicits._
import fs2.{Pipe, Segment, Stream}
import io.circe.{Json, ParsingFailure}
import io.circe.parser.parse
import org.http4s.{Headers, MediaType, Request, Uri}
import org.http4s.client.blaze.PooledHttp1Client
import org.http4s.dsl.io.POST
import org.http4s.headers.`Content-Type`
import tech.christopherdavenport.twitterstorm.authentication._
import tech.christopherdavenport.twitterstorm.twitter.BasicTweet
import tech.christopherdavenport.twitterstorm.util._

object Client {

  def twitterStreamRequest[F[_]: Effect]: Request[F] = Request[F](
    POST,
    Uri.unsafeFromString("https://stream.twitter.com/1.1/statuses/sample.json"),
//    Uri.unsafeFromString("https://stream.twitter.com/1.1/statuses/filter.json?track=uk%2Cus%2Cmedia%2Cusa%2Camerica%2Cuk%2Cchina&stall_warnings=true"),
    headers = Headers(`Content-Type`(MediaType.`application/x-www-form-urlencoded`))
  )

  //Cleaner Implementation, Be Careful FlatMapping on client.streaming with just the body as that runs each Byte
  // and loses fusion. Transform to Segments for performant code.
  def clientBodyStream[F[_]](implicit F: Effect[F]): Pipe[F, TwitterUserAuthentication, Segment[Byte, Unit]] =
    taS =>
      for {
        ta <- taS
        client <- Stream.emit(PooledHttp1Client(1))
        signedRequest <- Stream
          .repeatEval(F.pure(twitterStreamRequest))
          .through(userSign(ta)) // Endlessly Generate Stream
        infiniteEntityBody <- client.streaming(signedRequest)(_.body.segments)
      } yield infiniteEntityBody

  def clientStream[F[_]](implicit F: Effect[F]): Pipe[F, TwitterUserAuthentication, BasicTweet] =
    _.through(clientBodyStream)
      .through(circefs2.byteStreamParserS)
      .through(tweetPipeS[F])
      .through(filterLeft)

  def jsonPipeS[F[_]]: Pipe[F, Byte, Json] = s => {
    s.through(circefs2.byteStreamParser[F])
  }

  def tweetPipeS[F[_]]: Pipe[F, Json, Either[String, BasicTweet]] = _.map {
    _.as[BasicTweet].leftMap(_.message)
  }

  def jsonPipe[F[_]]: Pipe[F, Byte, Either[ParsingFailure, Json]] =
    _.through(fs2.text.utf8Decode).through(fs2.text.lines).map(parse)

  def tweetPipe[F[_]]: Pipe[F, Either[ParsingFailure, Json], Either[String, BasicTweet]] = _.map {
    _.fold(e => Either.left(e.message), j => j.as[BasicTweet].leftMap(_.message))
  }

}

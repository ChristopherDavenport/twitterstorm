package tech.christopherdavenport.twitterstorm

import cats.effect.Effect
import cats.implicits._
import org.log4s.getLogger
import fs2.{Pipe, Stream}
import io.circe.Json
import io.circe.parser.parse
import io.circe.ParsingFailure
import org.http4s.{Headers, MediaType, Request, Uri}
import org.http4s.client.blaze.PooledHttp1Client
import org.http4s.dsl.io.POST
import org.http4s.headers.`Content-Type`
import pureconfig.error.ConfigReaderFailures
import pureconfig.loadConfig
import tech.christopherdavenport.twitterstorm.authentication._
import tech.christopherdavenport.twitterstorm.twitter.BasicTweet
import tech.christopherdavenport.twitterstorm.util._

import scala.concurrent.ExecutionContext

object Client {

  def twitterStreamRequest[F[_]: Effect]: Request[F] = Request[F](
    POST,
    Uri.unsafeFromString("https://stream.twitter.com/1.1/statuses/sample.json"),
//    Uri.unsafeFromString("https://stream.twitter.com/1.1/statuses/filter.json?track=trump%2Cus%2Cmedia%2Cusa%2Camerica%2Cuk%2Cchina&stall_warnings=true"),
//    Uri.unsafeFromString("https://stream.twitter.com/1.1/statuses/filter.json?track=dev%2Cprogramming%2Ctech%2Cjava%2Crust%2Cscala%2Cpython&stall_warnings=true"),
    headers = Headers(`Content-Type`(MediaType.`application/x-www-form-urlencoded`))
  )

  def clientBodyStream[F[_]](ta: TwitterUserAuthentication)(implicit F: Effect[F]): Stream[F, Byte] = for {
    client <- Stream.emit(PooledHttp1Client(1))
    signedRequest <- Stream.repeatEval(F.pure(twitterStreamRequest)).through(userSign(ta)) // Endlessly Generate Stream
    infiniteEntityBody <- client.streaming(signedRequest)(_.body)
  } yield infiniteEntityBody

  def clientStream[F[_]](ta: TwitterUserAuthentication)(implicit F: Effect[F], ec: ExecutionContext): Stream[F, BasicTweet] = {
    clientBodyStream(ta)
      .through(jsonPipeS[F])
      .observe(_.map(_.pretty(_root_.io.circe.Printer.spaces2)).to(logSink(getLogger, TRACE, _.toString)))
      .through(tweetPipeS[F])
      .observe(logSink(getLogger, INFO, _.show))
      .through(filterLeft)
//      .observe(s => s.filter(_.entities.hashtags.nonEmpty).to(printSink))
  }

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

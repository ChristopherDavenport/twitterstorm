package tech.christopherdavenport.twitterstorm

import cats.effect.IO
import cats.implicits._
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
import tech.christopherdavenport.twitterstorm.authentication.TwitterAuthentication
import tech.christopherdavenport.twitterstorm.twitter.BasicTweet
import tech.christopherdavenport.twitterstorm.util._

import scala.concurrent.ExecutionContext

object Client {

  val configStream : Stream[IO, TwitterAuthentication] = Stream.eval(
    IO(loadConfig[TwitterAuthentication]("twitterstorm")).flatMap(validateOrError)
  )

  def validateOrError(e: Either[ConfigReaderFailures, TwitterAuthentication]): IO[TwitterAuthentication] = e match {
    case Left(errors) => IO.raiseError(new Throwable(errors.toList.map(_.description).toString()))
    case Right(r) => IO(r)
  }

  val twitterStreamRequest : Request[IO] = Request[IO](
    POST,
//    Uri.unsafeFromString("https://stream.twitter.com/1.1/statuses/sample.json"),
    Uri.unsafeFromString("https://stream.twitter.com/1.1/statuses/filter.json?track=trump%2Cus%2Cmedia%2Cusa%2Camerica%2Cuk%2Cchina&stall_warnings=true"),
//    Uri.unsafeFromString("https://stream.twitter.com/1.1/statuses/filter.json?track=dev%2Cprogramming%2Ctech%2Cjava%2Crust%2Cscala%2Cpython&stall_warnings=true"),
    headers = Headers(`Content-Type`(MediaType.`application/x-www-form-urlencoded`))
  )

  def clientStream(implicit ec: ExecutionContext) : Stream[IO, BasicTweet] = {
    configStream.flatMap { conf =>
      Stream.eval(
        authentication.userSign[IO](
          conf.consumerKey,
          conf.consumerSecret,
          conf.userKey,
          conf.userSecret
        )(twitterStreamRequest)
      )
    }.flatMap( signedRequest =>
      PooledHttp1Client[IO](5).streaming(signedRequest)(resp =>
        resp.body
          .through(jsonPipe[IO])
//          .observe(_.map(_.map(_.pretty(io.circe.Printer.noSpaces))).to(printSink))
          .through(tweetPipe[IO])
//           .observe(printSink)
          .through(filterLeft)
//          .observe(printSink)
      )
    )
  }

  def jsonPipeS[F[_]] : Pipe[F, Byte, Json] = s => {
    s.through(circefs2.byteStreamParser[F])
  }

  def tweetPipeS[F[_]]: Pipe[F, Json, Either[String, BasicTweet]] = _.map{ _.as[BasicTweet].leftMap(_.message)}

  def jsonPipe[F[_]]: Pipe[F, Byte, Either[ParsingFailure, Json]] =
    _.through(fs2.text.utf8Decode).through(fs2.text.lines).map(parse)

  def tweetPipe[F[_]]: Pipe[F, Either[ParsingFailure, Json], Either[String, BasicTweet]] = _.map{
    _.fold(e => Either.left(e.message), j => j.as[BasicTweet].leftMap(_.message))
  }



}

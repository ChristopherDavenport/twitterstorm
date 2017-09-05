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
import pureconfig.loadConfig
import tech.christopherdavenport.twitterstorm.authentication.TwitterAuthentication
import tech.christopherdavenport.twitterstorm.twitter.BasicTweet
import tech.christopherdavenport.twitterstorm.util._

object Client {

  val configStream : Stream[IO, TwitterAuthentication] = Stream.eval(
    IO(loadConfig[TwitterAuthentication]("twitterstorm").getOrElse(throw new Error("Config Error")))
  )

  val twitterStreamRequest : Request[IO] = Request[IO](
    POST,
    Uri.unsafeFromString("https://stream.twitter.com/1.1/statuses/sample.json"),
    //    Uri.unsafeFromString("https://stream.twitter.com/1.1/statuses/filter.json?track=trump&stall_warnings=true"),
    headers = Headers(`Content-Type`(MediaType.`application/x-www-form-urlencoded`))
  )

  val clientStream : Stream[IO, BasicTweet] = {
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
      PooledHttp1Client[IO](1).streaming(signedRequest)(resp =>
        resp.body
          .through(jsonPipe[IO])
          .through(tweetPipe)
          .through(filterLeft)
      )
    )
  }

  def jsonPipe[F[_]] : Pipe[F, Byte, Either[ParsingFailure, Json]] = s => {
    s.through(fs2.text.utf8Decode[F]).through(fs2.text.lines[F]).map(parse)
  }

  def tweetPipe[F[_]]: Pipe[F, Either[ParsingFailure, Json], Either[String, BasicTweet]] = _.map{
    _.fold(e => Either.left(e.message), j => j.as[BasicTweet].leftMap(_.message))
  }



}

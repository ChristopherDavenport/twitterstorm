package tech.christopherdavenport.twitterstorm
package emoji

import java.io.FileNotFoundException

import cats.effect.Effect
import cats.implicits._
import java.nio.file.Paths

import fs2._
import fs2.io._
import circefs2._
import util._
//import _root_.io.circe.

import scala.concurrent.ExecutionContext
import scala.util.Try

object EmojiParser {

  def emojiMapFromFile[F[_]](implicit F: Effect[F], ec: ExecutionContext): Stream[F, Map[Int, String]] = {
    val resource = Try(scala.io.Source.fromResource("emoji.json"))
    val lines = resource.map(_.getLines)
    val resourceLines = Stream.eval(F.fromTry(lines))
      .flatMap(i => Stream.emits(i.toSeq))

    val json = resourceLines
      .through(stringArrayParser)
      .through(decoder[F, Emoji])
      .filter(emoji => emoji.has_img_twitter.exists(identity))
      .filter(emoji => !emoji.unified.contains("-")) // Remove Multi Code Point For Initial Simplicity
      .map(emoji => Emoji.codePoint(emoji.unified) -> emoji)
      .filter(_._1.isDefined)
      .map{ case (k, v) => for {
        key <- k
        name <- v.name
      } yield key -> name
      }
      .unNone


    Stream.eval(
      json.runLog
      .map(_.toMap)
    )
  }

}

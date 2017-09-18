package tech.christopherdavenport.twitterstorm
package emoji

import cats.effect.Effect
import cats.implicits._
import fs2._
import circefs2._
import scodec.bits.ByteVector

import scala.util.Try

object EmojiParser {

  def emojiMapFromFile[F[_]](implicit F: Effect[F]): Stream[F, Map[ByteVector, String]] = {
    val resource = Try(scala.io.Source.fromResource("emoji.json"))
    val lines = resource.map(_.getLines)
    val resourceLines = Stream
      .eval(F.fromTry(lines))
      .flatMap(i => Stream.emits(i.toSeq))

    val json = resourceLines
      .through(stringArrayParser)
      .through(decoder[F, Emoji])
      .filter(_.has_img_twitter)
      .map(emoji => Emoji.hexStringToByteVector(emoji.unified) -> emoji)
      .filter(_._1.isDefined)
      .map {
        case (k, v) =>
          for {
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

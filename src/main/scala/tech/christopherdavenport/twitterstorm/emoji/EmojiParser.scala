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

    resourceLines
      .through(stringArrayParser)
      .through(decoder[F, Emoji])
      .through(emojiStreamToMap)
  }

  def emojiStreamToMap[F[_]](implicit F: Effect[F]): Pipe[F, Emoji, Map[ByteVector, String]] = emojis =>
    Stream.eval(
      emojis
        .filter(_.has_img_twitter)
        .map(emoji => ByteVector.fromHex(emoji.unified) -> emoji) // Parse from Hex, some longer regional emoji's not parsed.
        .filter(_._1.isDefined)
        .map {
          case (k, v) =>
            for {
              key <- k
              name <- v.name
            } yield key -> name
        }
        .unNone
        .runLog
        .map(_.toMap)
    )

}

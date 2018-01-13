package tech.christopherdavenport.twitterstorm
package emoji

import cats.effect.{Effect, Sync}
import cats.implicits._
import fs2._
import tech.christopherdavenport.twitterstorm.util.CirceStreaming._
import scodec.bits.ByteVector

import scala.util.Try

object EmojiParser {

  def emojiMapFromResource[F[_]](resourceName: String)(implicit F: Effect[F]): Stream[F, Map[ByteVector, String]] =
    emojisFromResource(resourceName).through(emojiStreamToMap)

  def emojisFromResource[F[_]](resourceName: String)(implicit F: Effect[F]): Stream[F, Emoji] = {
    def iteratorStream[A](i: Iterator[A]) : Stream[F, A] = Stream.emits(i.toSeq)
    Stream.eval(F.fromTry(Try(scala.io.Source.fromResource(resourceName)).map(_.getLines())))
      .flatMap(iteratorStream)
      .through(stringArrayParser)
      .through(decoder[F, Emoji])
  }

  def emojiStreamToMap[F[_]](implicit F: Sync[F]): Pipe[F, Emoji, Map[ByteVector, String]] = emojis =>
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
        .compile
        .toVector
        .map(_.toMap)
    )

}

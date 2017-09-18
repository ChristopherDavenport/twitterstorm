package tech.christopherdavenport.twitterstorm.emoji

import cats._
import cats.Id
import cats.implicits._
import _root_.io.circe.generic.JsonCodec
import cats.effect.{Effect, IO, Sync}
import scodec.bits.ByteVector
import fs2._

import scala.util.Try

@JsonCodec
case class Emoji(
    name: Option[String],
    unified: String,
    has_img_twitter: Boolean
)

object Emoji {

  def codePoint(string: String): Option[Int] = {
    val codePoint = Try(Integer.parseInt(string, 16))
      .recoverWith {
        case _ =>
          val stripped = string.replace("0x", "")
          Try(Integer.parseInt(stripped, 16))
      }
    codePoint.toOption
  }

  def hexStringToUTF8String(s: String): Option[String] = {
    s.split("-") // Seperate Each Hex Char
      .toVector
      .map(hex => Try(Integer.parseInt(hex, 16)).toOption)
      .map(_.flatMap(i => Try(i.toByte).toOption))
      .sequence[Option, Byte]
      .map(
        Stream.emits(_)
          .through(text.utf8Decode)
          .covary[IO]  // Pure Computation Would be Nice to Avoid IO
          .runLast
      )
      .sequence[IO, Option[String]]
      .map(_.flatten)
      .unsafeRunSync()
  }

  def hexStringToByteVector(s: String): Option[ByteVector] = {
    s.split("-")
      .toVector
      .map(hex => Try(Integer.parseInt(hex, 16)).toOption)
      .map(_.flatMap(i => Try(i.toByte).toOption))
      .sequence[Option, Byte]
      .map(ByteVector(_))
  }

}

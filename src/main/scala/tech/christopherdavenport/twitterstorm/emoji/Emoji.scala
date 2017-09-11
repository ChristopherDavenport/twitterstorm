package tech.christopherdavenport.twitterstorm.emoji

import _root_.io.circe.generic.JsonCodec
import _root_.io.circe.Json
import scodec.bits._

import scala.util.Try

@JsonCodec
case class Emoji(
                name: Option[String],
                unified: String,
//                variations: List[String],
//                docomo: Option[String],
//                au: Option[String],
//                softbank: Option[String],
//                google: Option[String],
//                image: Option[String],
//                sheet_x: Int,
//                sheet_y: Int,
//                short_name: Option[String],
//                short_names: List[String],
//                text: Option[String],
//                texts: Option[List[String]],
//                category: String,
//                sort_order: Int,
//                added_in: String,
//                has_img_app: Option[Boolean],
//                has_img_google: Option[Boolean],
                has_img_twitter: Option[Boolean],
//                has_img_emojione: Option[Boolean],
//                has_img_facebook: Option[Boolean],
//                has_img_messenger: Option[Boolean],
//                obsoletes: Option[String]
                )

object Emoji {


  def codePoint(string: String): Option[Int] = {
    val codePoint = Try(Integer.parseInt(string, 16))
      .recoverWith {
        case e: NumberFormatException =>
          val stripped = string.replace("0x", "")
          Try(Integer.parseInt(stripped, 16))
      }
    codePoint.toOption
  }

}

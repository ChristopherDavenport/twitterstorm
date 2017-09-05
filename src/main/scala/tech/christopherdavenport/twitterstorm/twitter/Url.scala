package tech.christopherdavenport.twitterstorm.twitter

import io.circe.generic.JsonCodec
import cats.Eq

@JsonCodec
case class Url(
               url: String,
               expanded_url: String,
               display_url: String,
               indices: List[Int]
               )

object Url {
  implicit val urlEq : Eq[Url] = Eq.fromUniversalEquals[Url]
}

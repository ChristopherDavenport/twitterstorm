package tech.christopherdavenport.twitterstorm.twitter

import io.circe.generic.JsonCodec
import cats.Eq

@JsonCodec
case class Symbol(
    text: String,
    indices: List[Int]
)

object Symbol {
  implicit val symbolEq = Eq.fromUniversalEquals[Symbol]
}

package tech.christopherdavenport.twitterstorm.twitter

import io.circe.generic.JsonCodec
import cats.Eq

@JsonCodec
case class Hashtag(
                   text: String,
                   indices: List[Int]
                   )

object Hashtag {

  implicit val hashTagEq : Eq[Hashtag] = Eq.fromUniversalEquals[Hashtag]
}

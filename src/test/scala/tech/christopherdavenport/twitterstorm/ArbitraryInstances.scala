package tech.christopherdavenport.twitterstorm

import java.time.{Instant, ZoneId, ZonedDateTime}

import cats.effect._
import cats.implicits._
import org.http4s.testing.{ArbitraryInstances => ArbInst}
import org.scalacheck._
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen
import org.scalacheck.Gen._
import tech.christopherdavenport.twitterstorm.twitter._
import fs2._
import scala.concurrent.Future

trait ArbitraryInstances extends ArbInst {




  implicit val arbZonedDateTime : Arbitrary[ZonedDateTime] = Arbitrary{
    val min = ZonedDateTime.of(1900, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")).toInstant.toEpochMilli
    val max = ZonedDateTime.of(9999, 12, 31, 23, 59, 59, 0, ZoneId.of("UTC")).toInstant.toEpochMilli
    choose[Long](min, max).map(l => ZonedDateTime.ofInstant(Instant.ofEpochMilli(l), ZoneId.of("UTC")))
  }

  implicit val arbHashTag : Arbitrary[Hashtag] = Arbitrary{
    for {
      hashtag <- arbString.arbitrary suchThat (_.length < 20)
      index <- Gen.choose(0, 120 - hashtag.length)
    } yield Hashtag(hashtag, List(index, index + hashtag.length))
  }

  implicit val arbUrl: Arbitrary[Url] = Arbitrary{
    for {
      ext <- arbString.arbitrary
      uri <- arbitraryUri.arbitrary
      index <- Gen.choose(0, 97)
    } yield {
      val link = show"t.co/$ext"
      val fullTLink = "https://" + link
      val shortLink = link.take(23)
      Url(
        fullTLink,
        uri.renderString,
        shortLink,
        List(index, index + 23)
      )
    }
  }

  implicit val arbUserMention : Arbitrary[UserMention] = Arbitrary{
    for {
      name <- arbitrary[String]
      userN <- arbitrary[String]
      id <- arbitrary[BigInt]
      index <- Gen.choose(0, 120)
    } yield UserMention(id, id.show, userN, name, List(index, index + userN.length))
  }

  implicit val arbSymbol : Arbitrary[Symbol] = Arbitrary{
    for {
      text <- arbitrary[String]
      index <- Gen.choose(0, 120)
    } yield {
      Symbol(text, List(index, index + text.length))
    }
  }

  implicit val arbEntities : Arbitrary[Entities] = Arbitrary{
    for {
      hashtags <- listOf(arbitrary[Hashtag])
      urls <- listOf(arbitrary[Url])
      mentions <- listOf(arbitrary[UserMention])
      symbols <- listOf(arbitrary[Symbol])
    } yield {
      Entities(hashtags, urls, mentions, symbols)
    }
  }

  implicit val arbBasicTweet : Arbitrary[BasicTweet] = Arbitrary{
    for {
      dateTime <- arbitrary[ZonedDateTime]
      id <- arbitrary[BigInt]
      text <- arbitrary[String]
      entities <- arbitrary[Entities]
      quoteC <- arbitrary[BigInt]
      replyC <- arbitrary[BigInt]
      retweetC <- arbitrary[BigInt]
      favoriteC <- arbitrary[BigInt]
    } yield BasicTweet(dateTime, id, text, entities, quoteC, replyC, retweetC, favoriteC)
  }


}

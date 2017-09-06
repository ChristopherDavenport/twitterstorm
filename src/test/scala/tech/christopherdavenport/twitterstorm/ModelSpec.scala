package tech.christopherdavenport.twitterstorm

import cats.effect.IO
import cats._
import cats.implicits._
import io.circe.ParsingFailure
import org.specs2.mutable.Specification
import tech.christopherdavenport.twitterstorm.twitter._

class ModelSpec extends Specification {

  val badBasic : BasicTweet =  BasicTweet(
    "",
    BigInt(0),
  "BAD MESSAGE",
    Entities(List.empty[Hashtag], List.empty[Url], List.empty[UserMention], List.empty[Symbol]),
  BigInt(0),
  BigInt(0),
  BigInt(0),
  BigInt(0)

  )

  val exampleTweet: String =
    """{
      |"created_at":"Tue Aug 29 01:06:11 +0000 2017",
      |"id":902336545302216706,
      |"id_str":"902336545302216706",
      |"text":"J'cogite trop",
      |"source":"\u003ca href=\"http:\/\/twitter.com\/download\/iphone\" rel=\"nofollow\"\u003eTwitter for iPhone\u003c\/a\u003e",
      |"truncated":false,
      |"in_reply_to_status_id":null,
      |"in_reply_to_status_id_str":null,
      |"in_reply_to_user_id":null,
      |"in_reply_to_user_id_str":null,
      |"in_reply_to_screen_name":null,
      |"user":{
      |  "id":3119066122,
      |  "id_str":"3119066122",
      |  "name":"Luis Gallego",
      |  "screen_name":"Majic92_",
      |  "location":"Corleone, Sicilia",
      |  "url":"https:\/\/www.instagram.com\/guillaumemjx\/",
      |  "description":"92 \ud83c\uddee\ud83c\uddf9 | \ud83d\udc7b: xguillaume92x",
      |  "translator_type":"none",
      |  "protected":false,
      |  "verified":false,
      |  "followers_count":338,
      |  "friends_count":213,
      |  "listed_count":7,
      |  "favourites_count":3080,
      |  "statuses_count":16260,
      |  "created_at":"Thu Mar 26 15:45:55 +0000 2015",
      |  "utc_offset":null,
      |  "time_zone":null,
      |  "geo_enabled":true,
      |  "lang":"fr",
      |  "contributors_enabled":false,
      |  "is_translator":false,
      |  "profile_background_color":"C0DEED",
      |  "profile_background_image_url":"http:\/\/abs.twimg.com\/images\/themes\/theme1\/bg.png",
      |  "profile_background_image_url_https":"https:\/\/abs.twimg.com\/images\/themes\/theme1\/bg.png",
      |  "profile_background_tile":false,
      |  "profile_link_color":"1DA1F2",
      |  "profile_sidebar_border_color":"C0DEED",
      |  "profile_sidebar_fill_color":"DDEEF6",
      |  "profile_text_color":"333333",
      |  "profile_use_background_image":true,
      |  "profile_image_url":"http:\/\/pbs.twimg.com\/profile_images\/900513966828421120\/yNsXPmlV_normal.jpg",
      |  "profile_image_url_https":"https:\/\/pbs.twimg.com\/profile_images\/900513966828421120\/yNsXPmlV_normal.jpg",
      |  "profile_banner_url":"https:\/\/pbs.twimg.com\/profile_banners\/3119066122\/1487875960",
      |  "default_profile":true,
      |  "default_profile_image":false,
      |  "following":null,
      |  "follow_request_sent":null,
      |  "notifications":null
      |},
      |"geo":null,
      |"coordinates":null,
      |"place":null,
      |"contributors":null,
      |"is_quote_status":false,
      |"quote_count":0,
      |"reply_count":0,
      |"retweet_count":0,
      |"favorite_count":0,
      |"entities":{
      |  "hashtags":[],
      |  "urls":[],
      |  "user_mentions":[],
      |  "symbols":[]
      |},
      |"favorited":false,
      |"retweeted":false,
      |"filter_level":"low",
      |"lang":"fr",
      |"timestamp_ms":"1503968771659"
      |}
    """.stripMargin

  val deleteExample : String =
    """{"delete":{
      |  "status":{
      |    "id":749373956830818304,
      |    "id_str":"749373956830818304",
      |    "user_id":749369250230378497,
      |    "user_id_str":"749369250230378497"
      |    },
      |  "timestamp_ms":"1503968771461"
      |}}
      |
      |
    """.stripMargin


  "BasicTweet Decoder" should {
    "decode a valid Tweet through parsing" in {
      val tweetParser = fs2.Stream.emit(io.circe.parser.parse(exampleTweet))
        .covary[IO]
        .through(Client.tweetPipe)
        .runLast
        .unsafeRunSync()
        .get

      tweetParser should beRight

    }

    "decode into a matching tweet" in {
      val tweetParser = fs2.Stream.emit(io.circe.parser.parse(exampleTweet))
        .covary[IO]
        .through(Client.tweetPipe)
        .runLast
        .unsafeRunSync()
        .get

      val basicTweet = BasicTweet(
        "Tue Aug 29 01:06:11 +0000 2017",
        BigInt("902336545302216706"),
        "J'cogite trop",
        Entities(List.empty[Hashtag], List.empty[Url], List.empty[UserMention], List.empty[Symbol]),
        BigInt(0),
        BigInt(0),
        BigInt(0),
        BigInt(0)
      )


      val resultTweet = tweetParser.getOrElse(badBasic)
      val result: Boolean = resultTweet === basicTweet

      result should beTrue
    }

    "fail to decode a delete response" in {
      val tweetParser = fs2.Stream.emit(io.circe.parser.parse(deleteExample))
        .covary[IO]
        .through(Client.tweetPipe)
        .runLast
        .unsafeRunSync()
        .get

      tweetParser should beLeft
    }


  }



}

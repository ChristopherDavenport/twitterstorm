package tech.christopherdavenport.twitterstorm.twitter

case class Media(
                id: BigInt,
                id_str: String,
                media_url: String,
                media_url_https: String,
                url: String,
                display_url: String,
                expanded_url: String,
                sizes: Sizes,
                `type`: String,
                indices: List[Int]
                )

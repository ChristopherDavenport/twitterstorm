package tech.christopherdavenport.twitterstorm.twitter

case class ExtendedEntities(
    id: BigInt,
    id_str: String,
    indices: List[Int],
    media_url: String,
    media_url_https: String,
    url: String,
    display_url: String,
    expanded_url: String,
    `type`: String,
    sizes: Sizes,
    ext_alt_text: String,
    video_info: Option[List[Int]]
)

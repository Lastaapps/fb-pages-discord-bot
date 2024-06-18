import cz.lastaapps.*
import io.kotest.core.spec.style.StringSpec

private const val pageName = "siliconhill"

class FacebookTests : StringSpec({
    val client = createClient()
    "feed" {
        val body = downloadFeed(client, pageName)
        FacebookFeedParser.parsePostsFromFeed(body).forEach(::println)
    }
    "post_with_reference_and_an_image_in_it" {
        val body = downloadPost(
            client,
            "100064590618084",
            "pfbid0FEtvWKB8pH5eB1mWGBT8MGQssGu7xdLsLvRkmzQ5D6Wdy4Gs6GbrLzUwcxWwXVz7l"
        )
        FacebookPostParser.parsePost(body).forEach(::println)
    }
    "post_post_with_reference_and_link" {
        val body = downloadPost(
            client,
            "100064590618084",
            "pfbid0hqVHBmAeCZHhJzu1YJBHqJwf83nfJjsjdju5CYNzamNFfMsViW3Tp6i688UQRGMsl"
        )
        FacebookPostParser.parsePost(body).forEach(::println)
    }
    "post_with_instagram_link" {
        val body = downloadPost(
            client,
            "100064590618084",
            "pfbid02oVfUUntLMg1HrnKGjhgu27TQrLLKjR72GJQqBmxUpYtQuzf3NSte6w51DYBP5jQal"
        )
        FacebookPostParser.parsePost(body).forEach(::println)
    }
    "post_with_event" {
        val body = downloadPost(
            client,
            "100064590618084",
            "pfbid02NEq2vQdG8uVqGr1jR5AbYynHQEHmAfkP7P317cadaJis98GHerB5frS56Jsdv8VRl"
        )
        FacebookPostParser.parsePost(body).forEach(::println)
    }
})
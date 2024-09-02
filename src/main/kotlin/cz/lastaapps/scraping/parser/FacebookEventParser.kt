package cz.lastaapps.scraping.parser

import cz.lastaapps.scraping.model.Event
import cz.lastaapps.scraping.parser.FacebookCommonParser.parseImages
import it.skrape.core.htmlDocument

object FacebookEventParser {
    fun parseEvent(
        body: String,
        eventId: String,
    ) = htmlDocument(body) {
        findFirst("#objects_container table tr td") {
            val img = parseImages().getOrNull(1) // first image is a calendar emoticon
            val title = findFirst("header").text

            val dateTime = findFirst("#event_summary table").findFirst("td dt div").text
            val where = findLast("#event_summary table").findFirst("td dt div").text

            val description =
                findFirst("#event_tabs header")
                    .parent.parent.parent
                    .findFirst("section")
                    .wholeText
                    .trimLines()

            Event(
                id = eventId,
                img = img,
                title = title,
                description = description,
                where = where,
                dateTime = dateTime,
            )
        }
    }
}

package cz.lastaapps.model

data class Event(
    val id: String,
    val img: String?,
    val title: String,
    val description: String,
    val dateTime: String,
    val where: String,
)

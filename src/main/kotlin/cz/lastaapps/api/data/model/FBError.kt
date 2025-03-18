package cz.lastaapps.api.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FBError(
    @SerialName("message")
    val message: String,
    @SerialName("type")
    val type: String,
    @SerialName("code")
    val code: Int,
    @SerialName("error_subcode")
    val errorSubcode: Int? = null,
    @SerialName("error_user_title")
    val errorUserTitle: String? = null,
    @SerialName("error_user_msg")
    val errorUserMsg: String? = null,
    @SerialName("fbtrace_id")
    val fbtraceId: String,
) {
    @Serializable
    data class Wrapper(
        @SerialName("error")
        val error: FBError,
    )
}

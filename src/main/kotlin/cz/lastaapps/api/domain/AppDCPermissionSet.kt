package cz.lastaapps.api.domain

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions

sealed interface AppDCPermissionSet {
    val permissions: Permissions

    object Posting : AppDCPermissionSet {
        override val permissions =
            Permissions(
                Permission.ViewChannel,
                Permission.SendMessages,
                Permission.EmbedLinks,
                Permission.AttachFiles,
            )
    }

    object Management : AppDCPermissionSet {
        override val permissions =
            Permissions(
                Permission.ManageMessages,
                Permission.ReadMessageHistory,
            )
    }

    object Events : AppDCPermissionSet {
        override val permissions =
            Permissions(
                Permission.CreateEvents,
                Permission.ManageEvents,
            )
    }

    companion object {
        val entries =
            listOf(
                Posting,
                Management,
                Events,
            )

        fun Map<AppDCPermissionSet, Boolean>.stringify() =
            this.entries.associate { (key, value) ->
                when (key) {
                    Events -> "events"
                    Management -> "management"
                    Posting -> "posting"
                } to value
            }
    }
}

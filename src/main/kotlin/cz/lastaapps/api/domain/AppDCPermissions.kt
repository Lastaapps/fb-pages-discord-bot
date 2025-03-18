package cz.lastaapps.api.domain

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions

object AppDCPermissions {
    val forPosting = Permissions(
        Permission.ViewChannel,
        Permission.SendMessages,
        Permission.EmbedLinks,
        Permission.AttachFiles,
    )
    val forManagement = forPosting + Permissions(
        Permission.ManageMessages,
        Permission.ReadMessageHistory,
    )
    val forEvents = Permissions(
        Permission.CreateEvents,
        Permission.ManageEvents,
    )
    val all = forPosting + forManagement + forEvents
}

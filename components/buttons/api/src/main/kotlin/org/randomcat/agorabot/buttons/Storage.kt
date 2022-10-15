package org.randomcat.agorabot.buttons

import java.time.Instant

interface ButtonRequestDescriptor

@JvmInline
value class ButtonRequestId(val raw: String)

data class ButtonRequestData(
    val descriptor: ButtonRequestDescriptor,
    val expiry: Instant,
)

interface ButtonRequestDataMap {
    fun tryGetRequestById(id: ButtonRequestId, timeForExpirationCheck: Instant): ButtonRequestDescriptor?
    fun putRequest(data: ButtonRequestData): ButtonRequestId
}

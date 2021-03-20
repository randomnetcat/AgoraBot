package org.randomcat.agorabot.util

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun OffsetDateTime.utcLocalDateTime(): LocalDateTime = withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()

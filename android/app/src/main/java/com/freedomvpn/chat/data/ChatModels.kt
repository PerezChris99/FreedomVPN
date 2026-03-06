package com.freedomvpn.chat.data

import java.util.UUID

/**
 * Chat message data model
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val contactKey: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isMe: Boolean,
    val status: MessageStatus = MessageStatus.SENT,
    val disappearAt: Long? = null
)

enum class MessageStatus {
    SENDING, SENT, FAILED
}

/**
 * Chat conversation data model
 */
data class ChatConversation(
    val contactKey: String,
    val contactName: String,
    val lastMessage: String = "",
    val lastTimestamp: Long = 0,
    val unread: Int = 0,
    val disappearTimer: DisappearTimer = DisappearTimer.OFF
)

enum class DisappearTimer(val label: String, val millis: Long) {
    OFF("Off", 0),
    SECONDS_30("30s", 30_000),
    MINUTES_5("5m", 5 * 60_000),
    HOUR_1("1h", 60 * 60_000),
    HOURS_24("24h", 24 * 60 * 60_000)
}

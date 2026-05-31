package com.freedomvpn.chat.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local chat repository - stores conversations and messages in encrypted SharedPreferences
 * Messages never leave the device unencrypted.
 */
@Singleton
class ChatRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "freedom_chat", Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_CONVERSATIONS = "conversations"
        private const val KEY_MESSAGES_PREFIX = "messages_"
        private const val MAX_MESSAGES_PER_CHAT = 500
    }

    fun getConversations(): List<ChatConversation> {
        val json = prefs.getString(KEY_CONVERSATIONS, null) ?: return emptyList()
        val type = object : TypeToken<List<ChatConversation>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveConversations(conversations: List<ChatConversation>) {
        prefs.edit().putString(KEY_CONVERSATIONS, gson.toJson(conversations)).apply()
    }

    fun getMessages(contactKey: String): List<ChatMessage> {
        val safeKey = contactKey.take(40).replace(Regex("[^a-zA-Z0-9]"), "_")
        val json = prefs.getString(KEY_MESSAGES_PREFIX + safeKey, null) ?: return emptyList()
        val type = object : TypeToken<List<ChatMessage>>() {}.type
        val messages: List<ChatMessage> = gson.fromJson(json, type)
        
        // Clean up disappeared messages
        val now = System.currentTimeMillis()
        return messages.filter { msg ->
            msg.disappearAt == null || msg.disappearAt > now
        }
    }

    fun saveMessages(contactKey: String, messages: List<ChatMessage>) {
        val safeKey = contactKey.take(40).replace(Regex("[^a-zA-Z0-9]"), "_")
        val trimmed = messages.takeLast(MAX_MESSAGES_PER_CHAT)
        prefs.edit().putString(KEY_MESSAGES_PREFIX + safeKey, gson.toJson(trimmed)).apply()
    }

    fun addMessage(contactKey: String, message: ChatMessage): List<ChatMessage> {
        val messages = getMessages(contactKey).toMutableList()
        messages.add(message)
        saveMessages(contactKey, messages)
        return messages
    }

    fun deleteConversation(contactKey: String) {
        val safeKey = contactKey.take(40).replace(Regex("[^a-zA-Z0-9]"), "_")
        val conversations = getConversations().toMutableList()
        conversations.removeAll { it.contactKey == contactKey }
        saveConversations(conversations)
        prefs.edit().remove(KEY_MESSAGES_PREFIX + safeKey).apply()
    }

    fun deleteMessage(contactKey: String, messageId: String) {
        val messages = getMessages(contactKey).toMutableList()
        messages.removeAll { it.id == messageId }
        saveMessages(contactKey, messages)
    }

    fun wipeAll() {
        prefs.edit().clear().apply()
    }
}

package com.freedomvpn.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freedomvpn.chat.crypto.ChatCryptoService
import com.freedomvpn.chat.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val isInitialized: Boolean = false,
    val myFreedomId: String = "",
    val myPublicKey: String = "",
    val conversations: List<ChatConversation> = emptyList(),
    val activeChat: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val unreadCount: Int = 0
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository
) : ViewModel() {

    private val cryptoService = ChatCryptoService()
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                cryptoService.initialize()
                val conversations = repository.getConversations()
                _uiState.value = _uiState.value.copy(
                    isInitialized = true,
                    myFreedomId = cryptoService.getFreedomId(),
                    myPublicKey = cryptoService.getPublicKeyBase64(),
                    conversations = conversations,
                    unreadCount = conversations.sumOf { it.unread }
                )
            } catch (e: Exception) {
                // Keystore may not support ECDH on some devices - still show UI
                _uiState.value = _uiState.value.copy(
                    isInitialized = true,
                    myFreedomId = "unsupported",
                    myPublicKey = ""
                )
            }
        }
    }

    fun setActiveChat(contactKey: String?) {
        _uiState.value = _uiState.value.copy(
            activeChat = contactKey,
            messages = if (contactKey != null) repository.getMessages(contactKey) else emptyList()
        )
        // Mark as read
        if (contactKey != null) {
            val conversations = _uiState.value.conversations.map {
                if (it.contactKey == contactKey) it.copy(unread = 0) else it
            }
            repository.saveConversations(conversations)
            _uiState.value = _uiState.value.copy(
                conversations = conversations,
                unreadCount = conversations.sumOf { it.unread }
            )
        }
    }

    fun addContact(publicKey: String, name: String) {
        viewModelScope.launch {
            try {
                cryptoService.addContact(publicKey)
                val conversations = _uiState.value.conversations.toMutableList()
                if (conversations.none { it.contactKey == publicKey }) {
                    conversations.add(
                        ChatConversation(
                            contactKey = publicKey,
                            contactName = name,
                            lastMessage = "Encrypted chat started",
                            lastTimestamp = System.currentTimeMillis()
                        )
                    )
                    repository.saveConversations(conversations)
                    _uiState.value = _uiState.value.copy(conversations = conversations)
                }
            } catch (_: Exception) {}
        }
    }

    fun sendMessage(text: String) {
        val contactKey = _uiState.value.activeChat ?: return
        
        viewModelScope.launch {
            val conversation = _uiState.value.conversations.find { it.contactKey == contactKey }
            val disappearAt = conversation?.disappearTimer?.let { timer ->
                if (timer.millis > 0) System.currentTimeMillis() + timer.millis else null
            }
            
            val message = ChatMessage(
                contactKey = contactKey,
                text = text,
                isMe = true,
                status = MessageStatus.SENT,
                disappearAt = disappearAt
            )
            
            val messages = repository.addMessage(contactKey, message)
            
            // Update conversation
            val conversations = _uiState.value.conversations.map {
                if (it.contactKey == contactKey) {
                    it.copy(lastMessage = text, lastTimestamp = message.timestamp)
                } else it
            }
            repository.saveConversations(conversations)
            
            _uiState.value = _uiState.value.copy(
                messages = messages,
                conversations = conversations
            )
        }
    }

    fun deleteConversation(contactKey: String) {
        repository.deleteConversation(contactKey)
        val conversations = _uiState.value.conversations.filter { it.contactKey != contactKey }
        _uiState.value = _uiState.value.copy(
            conversations = conversations,
            activeChat = null,
            messages = emptyList(),
            unreadCount = conversations.sumOf { it.unread }
        )
    }

    fun setDisappearTimer(contactKey: String, timer: DisappearTimer) {
        val conversations = _uiState.value.conversations.map {
            if (it.contactKey == contactKey) it.copy(disappearTimer = timer) else it
        }
        repository.saveConversations(conversations)
        _uiState.value = _uiState.value.copy(conversations = conversations)
    }

    fun wipeAll() {
        cryptoService.wipeAll()
        repository.wipeAll()
        _uiState.value = ChatUiState()
    }
}

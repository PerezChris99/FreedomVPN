package com.freedomvpn.chat.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomvpn.chat.data.ChatConversation
import com.freedomvpn.chat.data.ChatMessage
import com.freedomvpn.chat.data.DisappearTimer
import com.freedomvpn.chat.viewmodel.ChatUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val GreenAccent = Color(0xFF10B981)
private val GreenDark = Color(0xFF065F46)
private val DarkBg = Color(0xFF0F172A)
private val CardBg = Color(0xFF1E293B)
private val CardBorder = Color(0xFF334155)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xFF94A3B8)

/**
 * Main Chat Screen with conversations list and active chat
 */
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onSetActiveChat: (String?) -> Unit,
    onSendMessage: (String) -> Unit,
    onAddContact: (String, String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onSetDisappearTimer: (String, DisappearTimer) -> Unit,
    onWipeAll: () -> Unit,
    onCopyToClipboard: (String) -> Unit
) {
    var showAddContact by remember { mutableStateOf(false) }
    var showMyId by remember { mutableStateOf(false) }

    if (!uiState.isInitialized) {
        // Loading state
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = GreenAccent, strokeWidth = 3.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Initializing Secure Chat...", color = TextPrimary, fontSize = 16.sp)
                Text("Generating encryption keys", color = TextSecondary, fontSize = 12.sp)
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            ChatHeader(
                freedomId = uiState.myFreedomId,
                onShowId = { showMyId = true },
                onAddContact = { showAddContact = true }
            )

            // Security Banner
            SecurityBanner()

            if (uiState.activeChat != null) {
                // Active Chat View
                val conversation = uiState.conversations.find { it.contactKey == uiState.activeChat }
                ActiveChatView(
                    conversation = conversation,
                    messages = uiState.messages,
                    onBack = { onSetActiveChat(null) },
                    onSendMessage = onSendMessage,
                    onDeleteConversation = { onDeleteConversation(uiState.activeChat) },
                    onSetDisappearTimer = { timer -> onSetDisappearTimer(uiState.activeChat, timer) }
                )
            } else {
                // Conversation List
                ConversationList(
                    conversations = uiState.conversations,
                    onSelectConversation = onSetActiveChat,
                    onAddContact = { showAddContact = true }
                )
            }
        }

        // Modals
        if (showAddContact) {
            AddContactDialog(
                onDismiss = { showAddContact = false },
                onAdd = { key, name ->
                    onAddContact(key, name)
                    showAddContact = false
                }
            )
        }
        if (showMyId) {
            FreedomIdDialog(
                freedomId = uiState.myFreedomId,
                publicKey = uiState.myPublicKey,
                onDismiss = { showMyId = false },
                onCopy = onCopyToClipboard
            )
        }
    }
}

@Composable
private fun ChatHeader(
    freedomId: String,
    onShowId: () -> Unit,
    onAddContact: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = GreenAccent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Secure Chat",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "End-to-end encrypted • Zero knowledge",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onShowId,
                modifier = Modifier
                    .size(40.dp)
                    .background(CardBg, RoundedCornerShape(12.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = "My ID", tint = GreenAccent, modifier = Modifier.size(20.dp))
            }
            IconButton(
                onClick = onAddContact,
                modifier = Modifier
                    .size(40.dp)
                    .background(GreenAccent.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .border(1.dp, GreenAccent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Contact", tint = GreenAccent, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SecurityBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(GreenAccent.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .border(1.dp, GreenAccent.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = GreenAccent, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "All messages are end-to-end encrypted. No one can read them.",
            color = GreenAccent,
            fontSize = 11.sp
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun ConversationList(
    conversations: List<ChatConversation>,
    onSelectConversation: (String) -> Unit,
    onAddContact: () -> Unit
) {
    if (conversations.isEmpty()) {
        // Empty state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            Brush.linearGradient(listOf(GreenAccent.copy(0.2f), Color(0xFF06B6D4).copy(0.2f))),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = GreenAccent, modifier = Modifier.size(40.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("FreedomVPN Secure Chat", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Send encrypted messages that no one\ncan intercept or read.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onAddContact,
                    colors = ButtonDefaults.buttonColors(containerColor = GreenAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Contact")
                }
                Spacer(modifier = Modifier.height(24.dp))
                // Feature grid
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeatureChip("E2E Encrypted", Icons.Default.Shield, GreenAccent)
                    FeatureChip("Zero Knowledge", Icons.Default.VisibilityOff, Color(0xFFA78BFA))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeatureChip("Disappearing", Icons.Default.Timer, Color(0xFFFBBF24))
                    FeatureChip("No Phone/Email", Icons.Default.Key, Color(0xFF06B6D4))
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(conversations, key = { it.contactKey }) { conv ->
                ConversationItem(
                    conversation = conv,
                    onClick = { onSelectConversation(conv.contactKey) }
                )
            }
        }
    }
}

@Composable
private fun FeatureChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Row(
        modifier = Modifier
            .background(DarkBg.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun ConversationItem(conversation: ChatConversation, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(CardBg.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .border(1.dp, CardBorder.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    Brush.linearGradient(listOf(GreenAccent.copy(0.3f), Color(0xFF06B6D4).copy(0.3f))),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                conversation.contactName.take(2).uppercase(),
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.contactName,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (conversation.lastTimestamp > 0) {
                    Text(
                        formatTime(conversation.lastTimestamp),
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = GreenAccent.copy(0.5f),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    conversation.lastMessage.ifEmpty { "No messages yet" },
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (conversation.unread > 0) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(GreenAccent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${conversation.unread}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun ActiveChatView(
    conversation: ChatConversation?,
    messages: List<ChatMessage>,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onDeleteConversation: () -> Unit,
    onSetDisappearTimer: (DisappearTimer) -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Chat header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBg.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        Brush.linearGradient(listOf(GreenAccent.copy(0.3f), Color(0xFF06B6D4).copy(0.3f))),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    (conversation?.contactName ?: "??").take(2).uppercase(),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    conversation?.contactName ?: "Unknown",
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = GreenAccent, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("End-to-end encrypted", color = GreenAccent, fontSize = 11.sp)
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = TextSecondary)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DisappearTimer.entries.forEach { timer ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Timer, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (timer == DisappearTimer.OFF) "Disappear: Off" else "Disappear: ${timer.label}")
                                }
                            },
                            onClick = {
                                onSetDisappearTimer(timer)
                                showMenu = false
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Delete Conversation", color = Color(0xFFEF4444))
                            }
                        },
                        onClick = {
                            onDeleteConversation()
                            showMenu = false
                        }
                    )
                }
            }
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = GreenAccent.copy(0.5f), modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Messages are end-to-end encrypted", color = TextSecondary, fontSize = 13.sp)
                            Text("Send the first message!", color = TextSecondary.copy(0.5f), fontSize = 11.sp)
                        }
                    }
                }
            }
            items(messages, key = { it.id }) { msg ->
                MessageBubble(message = msg)
            }
        }

        // Input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBg.copy(alpha = 0.3f))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier
                    .weight(1f)
                    .background(DarkBg.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                decorationBox = { innerTextField ->
                    if (messageText.isEmpty()) {
                        Text("Type a secure message...", color = TextSecondary, fontSize = 14.sp)
                    }
                    innerTextField()
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (messageText.isNotBlank()) {
                        onSendMessage(messageText)
                        messageText = ""
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (messageText.isNotBlank()) GreenAccent else CardBg,
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (messageText.isNotBlank()) Color.White else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isMe) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    if (message.isMe) GreenAccent.copy(alpha = 0.2f) else CardBg.copy(alpha = 0.7f),
                    RoundedCornerShape(16.dp)
                )
                .border(
                    1.dp,
                    if (message.isMe) GreenAccent.copy(0.2f) else CardBorder.copy(0.3f),
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                message.text,
                color = if (message.isMe) TextPrimary else Color(0xFFCBD5E1),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Row(
                modifier = Modifier.align(if (message.isMe) Alignment.End else Alignment.Start),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatTime(message.timestamp),
                    color = TextSecondary.copy(0.6f),
                    fontSize = 10.sp
                )
                if (message.isMe) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        when (message.status) {
                            com.freedomvpn.chat.data.MessageStatus.SENT -> "✓"
                            com.freedomvpn.chat.data.MessageStatus.FAILED -> "✗"
                            com.freedomvpn.chat.data.MessageStatus.SENDING -> "⏳"
                        },
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun AddContactDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var publicKey by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = GreenAccent)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Secure Contact", color = TextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Paste their Freedom ID to connect", color = TextSecondary, fontSize = 12.sp)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Contact Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = publicKey,
                    onValueChange = { publicKey = it; error = "" },
                    label = { Text("Freedom ID (Public Key)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                )
                if (error.isNotEmpty()) {
                    Text(error, color = Color(0xFFEF4444), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (publicKey.isBlank()) {
                        error = "Please enter their Freedom ID"
                    } else if (publicKey.length < 20) {
                        error = "Invalid Freedom ID - too short"
                    } else {
                        onAdd(publicKey.trim(), name.trim().ifEmpty { "Unknown" })
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GreenAccent)
            ) {
                Text("Add Contact")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun FreedomIdDialog(
    freedomId: String,
    publicKey: String,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit
) {
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            Brush.linearGradient(listOf(GreenAccent.copy(0.3f), Color(0xFF06B6D4).copy(0.3f))),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = GreenAccent, modifier = Modifier.size(32.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Your Freedom ID", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Share this with contacts", color = TextSecondary, fontSize = 12.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Short ID
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkBg, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Short ID", color = TextSecondary, fontSize = 11.sp)
                    Text(
                        freedomId,
                        color = GreenAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 4.sp
                    )
                }
                // Full key
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkBg, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text("Full Public Key", color = TextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        publicKey,
                        color = Color(0xFFCBD5E1),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp
                    )
                }
                Text(
                    "Your private key never leaves this device.",
                    color = TextSecondary.copy(0.5f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCopy(publicKey)
                    copied = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = GreenAccent)
            ) {
                Icon(
                    if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (copied) "Copied!" else "Copy Key")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    )
}

private fun formatTime(timestamp: Long): String {
    val date = Date(timestamp)
    val now = Date()
    val sameDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(date) ==
            SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(now)
    return if (sameDay) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    } else {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(date)
    }
}

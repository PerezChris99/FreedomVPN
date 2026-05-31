/**
 * FreedomVPN Secure Messaging Service
 * 
 * Peer-to-peer encrypted messaging with zero-knowledge relay.
 * Messages are encrypted locally, sent through the VPN tunnel,
 * and relayed via a lightweight broker that sees only encrypted blobs.
 * 
 * Architecture:
 * - WebSocket connection through VPN tunnel to relay
 * - Messages are E2EE (end-to-end encrypted) before leaving device
 * - Relay stores encrypted blobs for max 24 hours
 * - No accounts, no phone numbers, no email - just cryptographic identity
 * - Perfect Forward Secrecy per message
 * - Disappearing messages support
 */

import cryptoService from './cryptoService'

const RELAY_RECONNECT_DELAY = 3000
const MAX_MESSAGE_AGE = 24 * 60 * 60 * 1000 // 24 hours
const DISAPPEAR_TIMES = {
  off: 0,
  '30s': 30 * 1000,
  '5m': 5 * 60 * 1000,
  '1h': 60 * 60 * 1000,
  '24h': 24 * 60 * 60 * 1000,
}

class MessagingService {
  constructor() {
    this.myPublicKey = null
    this.myFreedomId = null
    this.conversations = new Map() // contactPublicKey -> { messages, unread, disappearTime }
    this.listeners = new Set()
    this.ws = null
    this.isConnected = false
    this.relayUrl = null
    this._initialized = false
  }

  /**
   * Initialize the messaging service
   */
  async initialize() {
    if (this._initialized) return

    this.myPublicKey = await cryptoService.initialize()
    this.myFreedomId = await cryptoService.getFreedomId()

    // Load conversations from local storage
    this._loadConversations()

    this._initialized = true
    this._notify()
  }

  /**
   * Connect to relay server
   * In demo/local mode, messages are stored locally only
   */
  connectToRelay(relayUrl = null) {
    this.relayUrl = relayUrl

    if (!relayUrl) {
      // Local-only mode - messages stored on device
      this.isConnected = true
      this._notify()
      return
    }

    try {
      this.ws = new WebSocket(relayUrl)

      this.ws.onopen = () => {
        this.isConnected = true
        // Register our public key with relay
        this.ws.send(JSON.stringify({
          type: 'register',
          publicKey: this.myPublicKey
        }))
        this._notify()
      }

      this.ws.onmessage = async (event) => {
        try {
          const data = JSON.parse(event.data)
          if (data.type === 'message') {
            await this._handleIncomingMessage(data)
          }
        } catch (e) {
          console.error('Failed to handle message:', e)
        }
      }

      this.ws.onclose = () => {
        this.isConnected = false
        this._notify()
        // Auto-reconnect
        setTimeout(() => this.connectToRelay(relayUrl), RELAY_RECONNECT_DELAY)
      }

      this.ws.onerror = () => {
        this.isConnected = false
        this._notify()
      }
    } catch (e) {
      console.error('WebSocket connection failed:', e)
      // Fall back to local-only mode
      this.isConnected = true
      this._notify()
    }
  }

  /**
   * Send an encrypted message to a contact
   */
  async sendMessage(recipientPublicKey, text, type = 'text') {
    if (!this._initialized) await this.initialize()

    const timestamp = Date.now()
    const messageId = this._generateId()

    // Create message object
    const message = {
      id: messageId,
      sender: this.myPublicKey,
      recipient: recipientPublicKey,
      text,
      type,
      timestamp,
      status: 'sending',
      isMe: true
    }

    // Add to local conversation immediately (optimistic)
    this._addMessageToConversation(recipientPublicKey, message)

    try {
      // Encrypt the message content
      const encrypted = await cryptoService.encryptMessage(recipientPublicKey, JSON.stringify({
        text,
        type,
        timestamp,
        id: messageId,
        sender: this.myPublicKey
      }))

      // Send via relay if connected
      if (this.ws?.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({
          type: 'message',
          to: recipientPublicKey,
          from: this.myPublicKey,
          payload: encrypted,
          timestamp
        }))
      }

      // Mark as sent
      message.status = 'sent'
      this._updateMessage(recipientPublicKey, messageId, { status: 'sent' })
      this._saveConversations()
      this._notify()

    } catch (e) {
      console.error('Failed to send message:', e)
      message.status = 'failed'
      this._updateMessage(recipientPublicKey, messageId, { status: 'failed' })
      this._notify()
    }

    return messageId
  }

  /**
   * Handle incoming encrypted message
   */
  async _handleIncomingMessage(data) {
    try {
      const decrypted = JSON.parse(
        await cryptoService.decryptMessage(data.from, data.payload)
      )

      const message = {
        id: decrypted.id,
        sender: data.from,
        recipient: this.myPublicKey,
        text: decrypted.text,
        type: decrypted.type || 'text',
        timestamp: decrypted.timestamp,
        status: 'received',
        isMe: false
      }

      this._addMessageToConversation(data.from, message)
      this._saveConversations()
      this._notify()

    } catch (e) {
      console.error('Failed to decrypt message:', e)
    }
  }

  /**
   * Add a new contact and start a conversation
   */
  async addContact(publicKeyHex, name) {
    if (!this._initialized) await this.initialize()

    await cryptoService.addContact(publicKeyHex, name)

    if (!this.conversations.has(publicKeyHex)) {
      this.conversations.set(publicKeyHex, {
        messages: [],
        unread: 0,
        disappearTime: 0,
        contactName: name,
        lastActivity: Date.now()
      })
    } else {
      const conv = this.conversations.get(publicKeyHex)
      conv.contactName = name
    }

    this._saveConversations()
    this._notify()
  }

  /**
   * Get all conversations
   */
  getConversations() {
    const convos = []
    for (const [key, value] of this.conversations.entries()) {
      const lastMsg = value.messages[value.messages.length - 1]
      convos.push({
        contactPublicKey: key,
        contactName: value.contactName || this._shortenKey(key),
        lastMessage: lastMsg?.text || '',
        lastTimestamp: lastMsg?.timestamp || value.lastActivity || 0,
        unread: value.unread,
        messages: value.messages
      })
    }
    return convos.sort((a, b) => b.lastTimestamp - a.lastTimestamp)
  }

  /**
   * Get messages for a specific conversation
   */
  getMessages(contactPublicKey) {
    const conv = this.conversations.get(contactPublicKey)
    if (!conv) return []

    // Mark as read
    conv.unread = 0
    this._saveConversations()
    this._notify()

    return conv.messages
  }

  /**
   * Set disappearing message timer for a conversation
   */
  setDisappearTimer(contactPublicKey, timerKey) {
    const conv = this.conversations.get(contactPublicKey)
    if (conv) {
      conv.disappearTime = DISAPPEAR_TIMES[timerKey] || 0
      this._saveConversations()
      this._notify()
    }
  }

  /**
   * Delete a conversation
   */
  deleteConversation(contactPublicKey) {
    this.conversations.delete(contactPublicKey)
    this._saveConversations()
    this._notify()
  }

  /**
   * Delete a specific message
   */
  deleteMessage(contactPublicKey, messageId) {
    const conv = this.conversations.get(contactPublicKey)
    if (conv) {
      conv.messages = conv.messages.filter(m => m.id !== messageId)
      this._saveConversations()
      this._notify()
    }
  }

  /**
   * Subscribe to state changes
   */
  subscribe(listener) {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  /**
   * Wipe all messaging data (panic button)
   */
  wipeAll() {
    this.conversations.clear()
    localStorage.removeItem('freedom_conversations')
    cryptoService.wipeAll()
    this._initialized = false
    this._notify()
  }

  /**
   * Get total unread count
   */
  getUnreadCount() {
    let total = 0
    for (const conv of this.conversations.values()) {
      total += conv.unread
    }
    return total
  }

  /**
   * Get my Freedom ID
   */
  getMyFreedomId() {
    return this.myFreedomId
  }

  /**
   * Get my full public key
   */
  getMyPublicKey() {
    return this.myPublicKey
  }

  // ─── Internal Methods ─────────────────

  _addMessageToConversation(contactKey, message) {
    if (!this.conversations.has(contactKey)) {
      this.conversations.set(contactKey, {
        messages: [],
        unread: 0,
        disappearTime: 0,
        contactName: cryptoService.getContacts().find(c => c.publicKey === contactKey)?.name || this._shortenKey(contactKey),
        lastActivity: Date.now()
      })
    }

    const conv = this.conversations.get(contactKey)
    conv.messages.push(message)
    conv.lastActivity = message.timestamp

    if (!message.isMe) {
      conv.unread++
    }

    // Handle disappearing messages
    if (conv.disappearTime > 0) {
      setTimeout(() => {
        this.deleteMessage(contactKey, message.id)
      }, conv.disappearTime)
    }
  }

  _updateMessage(contactKey, messageId, updates) {
    const conv = this.conversations.get(contactKey)
    if (conv) {
      const msg = conv.messages.find(m => m.id === messageId)
      if (msg) Object.assign(msg, updates)
    }
  }

  _generateId() {
    return Date.now().toString(36) + Math.random().toString(36).substring(2, 8)
  }

  _shortenKey(key) {
    return key.substring(0, 8).toUpperCase() + '...'
  }

  _saveConversations() {
    const data = {}
    for (const [key, value] of this.conversations.entries()) {
      data[key] = {
        messages: value.messages.slice(-500), // Keep last 500 messages
        unread: value.unread,
        disappearTime: value.disappearTime,
        contactName: value.contactName,
        lastActivity: value.lastActivity
      }
    }
    localStorage.setItem('freedom_conversations', JSON.stringify(data))
  }

  _loadConversations() {
    try {
      const data = localStorage.getItem('freedom_conversations')
      if (data) {
        const parsed = JSON.parse(data)
        for (const [key, value] of Object.entries(parsed)) {
          this.conversations.set(key, {
            messages: value.messages || [],
            unread: value.unread || 0,
            disappearTime: value.disappearTime || 0,
            contactName: value.contactName || this._shortenKey(key),
            lastActivity: value.lastActivity || 0
          })
        }
      }
    } catch { /* ignore */ }

    // Clean up old disappearing messages
    this._cleanupExpiredMessages()
  }

  _cleanupExpiredMessages() {
    const now = Date.now()
    for (const conv of this.conversations.values()) {
      if (conv.disappearTime > 0) {
        conv.messages = conv.messages.filter(
          m => now - m.timestamp < conv.disappearTime
        )
      }
    }
  }

  _notify() {
    const state = {
      isConnected: this.isConnected,
      myFreedomId: this.myFreedomId,
      myPublicKey: this.myPublicKey,
      conversations: this.getConversations(),
      unreadCount: this.getUnreadCount()
    }
    for (const listener of this.listeners) {
      listener(state)
    }
  }
}

const messagingService = new MessagingService()
export default messagingService
export { DISAPPEAR_TIMES }

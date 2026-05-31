import { useState, useRef, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { useChat } from '../context/ChatContext'
import { useLanguage } from '../context/LanguageContext'
import {
  MessageCircle, Send, Plus, Shield, Lock, Copy, Check, Trash2, 
  UserPlus, ArrowLeft, Timer, AlertTriangle, Key, QrCode, Search,
  MoreVertical, ShieldCheck, Eye, EyeOff, Hash, Fingerprint
} from 'lucide-react'

export default function Chat() {
  const {
    isInitialized, isConnected, myFreedomId, myPublicKey,
    conversations, unreadCount, activeChat, setActiveChat,
    sendMessage, addContact, deleteConversation, deleteMessage,
    setDisappearTimer, getMessages, wipeAll, DISAPPEAR_TIMES
  } = useChat()
  const { t } = useLanguage()

  const [showAddContact, setShowAddContact] = useState(false)
  const [showMyId, setShowMyId] = useState(false)
  const [messageText, setMessageText] = useState('')
  const [searchQuery, setSearchQuery] = useState('')
  const [copiedId, setCopiedId] = useState(false)
  const [showMenu, setShowMenu] = useState(null)
  const messagesEndRef = useRef(null)
  const inputRef = useRef(null)

  const activeConversation = conversations.find(c => c.contactPublicKey === activeChat)
  const messages = activeChat ? getMessages(activeChat) : []

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages.length])

  useEffect(() => {
    if (activeChat && inputRef.current) {
      inputRef.current.focus()
    }
  }, [activeChat])

  const handleSend = async (e) => {
    e.preventDefault()
    if (!messageText.trim()) return
    await sendMessage(messageText)
    setMessageText('')
  }

  const copyFreedomId = () => {
    if (myPublicKey) {
      navigator.clipboard.writeText(myPublicKey)
      setCopiedId(true)
      setTimeout(() => setCopiedId(false), 2000)
    }
  }

  const formatTime = (ts) => {
    const d = new Date(ts)
    const now = new Date()
    const isToday = d.toDateString() === now.toDateString()
    if (isToday) return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    return d.toLocaleDateString([], { month: 'short', day: 'numeric' }) + ' ' +
      d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }

  const filteredConversations = searchQuery
    ? conversations.filter(c =>
        c.contactName.toLowerCase().includes(searchQuery.toLowerCase())
      )
    : conversations

  if (!isInitialized) {
    return (
      <div className="min-h-screen flex items-center justify-center p-4">
        <div className="text-center">
          <div className="w-16 h-16 rounded-full bg-green-500/20 flex items-center justify-center mx-auto mb-4 animate-pulse">
            <Shield className="w-8 h-8 text-green-400" />
          </div>
          <p className="text-white font-medium">Initializing Secure Chat...</p>
          <p className="text-slate-400 text-sm mt-1">Generating encryption keys</p>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen p-4 sm:p-6">
      {/* Page Header */}
      <div className="mb-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-white flex items-center gap-2">
              <Lock className="w-6 h-6 text-green-400" />
              Secure Chat
            </h1>
            <p className="text-slate-400 text-sm mt-1">End-to-end encrypted • Zero knowledge</p>
          </div>
          <div className="flex items-center gap-2">
            <motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              onClick={() => setShowMyId(true)}
              className="p-2.5 rounded-xl bg-slate-800/50 border border-slate-700/50 text-green-400 hover:bg-green-500/10 transition-colors"
              title="My Freedom ID"
            >
              <Fingerprint className="w-5 h-5" />
            </motion.button>
            <motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              onClick={() => setShowAddContact(true)}
              className="p-2.5 rounded-xl bg-green-500/20 border border-green-500/30 text-green-400 hover:bg-green-500/30 transition-colors"
            >
              <UserPlus className="w-5 h-5" />
            </motion.button>
          </div>
        </div>
      </div>

      {/* Security Banner */}
      <motion.div 
        className="bg-green-500/10 border border-green-500/20 rounded-xl p-3 mb-6"
        initial={{ opacity: 0, y: -10 }}
        animate={{ opacity: 1, y: 0 }}
      >
        <div className="flex items-center gap-2">
          <ShieldCheck className="w-4 h-4 text-green-400 flex-shrink-0" />
          <p className="text-green-400 text-xs">
            All messages are end-to-end encrypted. No one — not even FreedomVPN — can read them.
          </p>
        </div>
      </motion.div>

      {/* Main Chat Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 lg:gap-6" style={{ minHeight: 'calc(100vh - 280px)' }}>
        
        {/* Conversation List */}
        <div className={`lg:col-span-4 ${activeChat ? 'hidden lg:block' : ''}`}>
          <div className="bg-slate-800/50 backdrop-blur-sm rounded-2xl border border-slate-700/50 overflow-hidden h-full">
            {/* Search */}
            <div className="p-3 border-b border-slate-700/30">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                <input
                  type="text"
                  placeholder="Search conversations..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="w-full bg-slate-900/50 text-white text-sm rounded-xl pl-10 pr-4 py-2.5 border border-slate-700/30 focus:border-green-500/50 focus:outline-none"
                />
              </div>
            </div>

            {/* Conversations */}
            <div className="overflow-y-auto max-h-[60vh]">
              {filteredConversations.length === 0 ? (
                <div className="p-8 text-center">
                  <MessageCircle className="w-12 h-12 text-slate-600 mx-auto mb-3" />
                  <p className="text-slate-400 text-sm font-medium">No conversations yet</p>
                  <p className="text-slate-500 text-xs mt-1">Add a contact to start a secure chat</p>
                  <button
                    onClick={() => setShowAddContact(true)}
                    className="mt-4 px-4 py-2 bg-green-500/20 text-green-400 rounded-lg text-sm hover:bg-green-500/30 transition-colors"
                  >
                    <UserPlus className="w-4 h-4 inline mr-2" />
                    Add Contact
                  </button>
                </div>
              ) : (
                filteredConversations.map((conv) => (
                  <motion.div
                    key={conv.contactPublicKey}
                    whileHover={{ backgroundColor: 'rgba(255,255,255,0.03)' }}
                    onClick={() => setActiveChat(conv.contactPublicKey)}
                    className={`p-4 border-b border-slate-700/20 cursor-pointer transition-colors ${
                      activeChat === conv.contactPublicKey ? 'bg-green-500/10 border-l-2 border-l-green-400' : ''
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-full bg-gradient-to-br from-green-500/30 to-cyan-500/30 flex items-center justify-center flex-shrink-0">
                        <span className="text-white font-bold text-sm">
                          {conv.contactName.substring(0, 2).toUpperCase()}
                        </span>
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center justify-between">
                          <p className="text-white font-medium text-sm truncate">{conv.contactName}</p>
                          {conv.lastTimestamp > 0 && (
                            <span className="text-slate-500 text-xs flex-shrink-0">{formatTime(conv.lastTimestamp)}</span>
                          )}
                        </div>
                        <div className="flex items-center justify-between mt-0.5">
                          <p className="text-slate-400 text-xs truncate flex items-center gap-1">
                            <Lock className="w-3 h-3 text-green-500/50" />
                            {conv.lastMessage || 'No messages yet'}
                          </p>
                          {conv.unread > 0 && (
                            <span className="bg-green-500 text-white text-xs rounded-full w-5 h-5 flex items-center justify-center flex-shrink-0">
                              {conv.unread}
                            </span>
                          )}
                        </div>
                      </div>
                    </div>
                  </motion.div>
                ))
              )}
            </div>
          </div>
        </div>

        {/* Chat Window */}
        <div className={`lg:col-span-8 ${!activeChat ? 'hidden lg:flex' : 'flex'} flex-col`}>
          {!activeChat ? (
            /* Empty State */
            <div className="bg-slate-800/50 backdrop-blur-sm rounded-2xl border border-slate-700/50 flex-1 flex items-center justify-center">
              <div className="text-center p-8">
                <div className="w-20 h-20 rounded-full bg-gradient-to-br from-green-500/20 to-cyan-500/20 flex items-center justify-center mx-auto mb-4">
                  <Lock className="w-10 h-10 text-green-400" />
                </div>
                <h2 className="text-white font-bold text-xl mb-2">FreedomVPN Secure Chat</h2>
                <p className="text-slate-400 text-sm max-w-sm mx-auto mb-6">
                  Send encrypted messages that no government, ISP, or hacker can intercept or read.
                </p>
                <div className="grid grid-cols-2 gap-3 max-w-xs mx-auto">
                  <div className="bg-slate-900/50 rounded-xl p-3">
                    <Shield className="w-5 h-5 text-green-400 mx-auto mb-1" />
                    <p className="text-slate-300 text-xs">E2E Encrypted</p>
                  </div>
                  <div className="bg-slate-900/50 rounded-xl p-3">
                    <Eye className="w-5 h-5 text-purple-400 mx-auto mb-1" />
                    <p className="text-slate-300 text-xs">Zero Knowledge</p>
                  </div>
                  <div className="bg-slate-900/50 rounded-xl p-3">
                    <Timer className="w-5 h-5 text-yellow-400 mx-auto mb-1" />
                    <p className="text-slate-300 text-xs">Disappearing Msgs</p>
                  </div>
                  <div className="bg-slate-900/50 rounded-xl p-3">
                    <Key className="w-5 h-5 text-cyan-400 mx-auto mb-1" />
                    <p className="text-slate-300 text-xs">No Phone / Email</p>
                  </div>
                </div>
              </div>
            </div>
          ) : (
            /* Active Chat */
            <div className="bg-slate-800/50 backdrop-blur-sm rounded-2xl border border-slate-700/50 flex-1 flex flex-col overflow-hidden">
              {/* Chat Header */}
              <div className="p-4 border-b border-slate-700/30 flex items-center gap-3">
                <button
                  onClick={() => setActiveChat(null)}
                  className="lg:hidden p-2 hover:bg-slate-700/50 rounded-lg"
                >
                  <ArrowLeft className="w-5 h-5 text-slate-400" />
                </button>
                <div className="w-10 h-10 rounded-full bg-gradient-to-br from-green-500/30 to-cyan-500/30 flex items-center justify-center">
                  <span className="text-white font-bold text-sm">
                    {(activeConversation?.contactName || '??').substring(0, 2).toUpperCase()}
                  </span>
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-white font-medium truncate">{activeConversation?.contactName}</p>
                  <p className="text-green-400 text-xs flex items-center gap-1">
                    <Lock className="w-3 h-3" /> End-to-end encrypted
                  </p>
                </div>
                <div className="relative">
                  <button
                    onClick={() => setShowMenu(showMenu ? null : 'chat')}
                    className="p-2 hover:bg-slate-700/50 rounded-lg text-slate-400"
                  >
                    <MoreVertical className="w-5 h-5" />
                  </button>
                  <AnimatePresence>
                    {showMenu === 'chat' && (
                      <motion.div
                        initial={{ opacity: 0, scale: 0.95 }}
                        animate={{ opacity: 1, scale: 1 }}
                        exit={{ opacity: 0, scale: 0.95 }}
                        className="absolute right-0 top-12 bg-slate-800 border border-slate-700/50 rounded-xl shadow-xl z-50 py-2 w-48"
                      >
                        {Object.keys(DISAPPEAR_TIMES).map((key) => (
                          <button
                            key={key}
                            onClick={() => {
                              setDisappearTimer(activeChat, key)
                              setShowMenu(null)
                            }}
                            className="w-full text-left px-4 py-2 text-sm text-slate-300 hover:bg-slate-700/50 flex items-center gap-2"
                          >
                            <Timer className="w-4 h-4 text-yellow-400" />
                            {key === 'off' ? 'Disappear: Off' : `Disappear: ${key}`}
                          </button>
                        ))}
                        <div className="border-t border-slate-700/30 mt-1 pt-1">
                          <button
                            onClick={() => {
                              deleteConversation(activeChat)
                              setShowMenu(null)
                            }}
                            className="w-full text-left px-4 py-2 text-sm text-red-400 hover:bg-red-500/10 flex items-center gap-2"
                          >
                            <Trash2 className="w-4 h-4" />
                            Delete Conversation
                          </button>
                        </div>
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>
              </div>

              {/* Messages */}
              <div className="flex-1 overflow-y-auto p-4 space-y-3 min-h-0">
                {messages.length === 0 ? (
                  <div className="flex items-center justify-center h-full">
                    <div className="text-center">
                      <Lock className="w-8 h-8 text-green-400/50 mx-auto mb-2" />
                      <p className="text-slate-500 text-sm">Messages are end-to-end encrypted</p>
                      <p className="text-slate-600 text-xs mt-1">Send the first message!</p>
                    </div>
                  </div>
                ) : (
                  messages.map((msg) => (
                    <motion.div
                      key={msg.id}
                      initial={{ opacity: 0, y: 10 }}
                      animate={{ opacity: 1, y: 0 }}
                      className={`flex ${msg.isMe ? 'justify-end' : 'justify-start'}`}
                    >
                      <div
                        className={`max-w-[75%] rounded-2xl px-4 py-2.5 ${
                          msg.isMe
                            ? 'bg-green-500/20 border border-green-500/20 text-white'
                            : 'bg-slate-700/50 border border-slate-600/20 text-slate-200'
                        }`}
                      >
                        <p className="text-sm leading-relaxed break-words">{msg.text}</p>
                        <div className={`flex items-center gap-1 mt-1 ${msg.isMe ? 'justify-end' : ''}`}>
                          <span className="text-slate-500 text-[10px]">{formatTime(msg.timestamp)}</span>
                          {msg.isMe && (
                            <span className="text-[10px]">
                              {msg.status === 'sent' ? '✓' : msg.status === 'failed' ? '✗' : '⏳'}
                            </span>
                          )}
                        </div>
                      </div>
                    </motion.div>
                  ))
                )}
                <div ref={messagesEndRef} />
              </div>

              {/* Message Input */}
              <form onSubmit={handleSend} className="p-4 border-t border-slate-700/30">
                <div className="flex items-center gap-3">
                  <input
                    ref={inputRef}
                    type="text"
                    value={messageText}
                    onChange={(e) => setMessageText(e.target.value)}
                    placeholder="Type a secure message..."
                    className="flex-1 bg-slate-900/50 text-white rounded-xl px-4 py-3 border border-slate-700/30 focus:border-green-500/50 focus:outline-none text-sm"
                  />
                  <motion.button
                    whileHover={{ scale: 1.05 }}
                    whileTap={{ scale: 0.95 }}
                    type="submit"
                    disabled={!messageText.trim()}
                    className="p-3 bg-green-500 hover:bg-green-600 disabled:bg-slate-700 disabled:text-slate-500 text-white rounded-xl transition-colors"
                  >
                    <Send className="w-5 h-5" />
                  </motion.button>
                </div>
              </form>
            </div>
          )}
        </div>
      </div>

      {/* Add Contact Modal */}
      <AnimatePresence>
        {showAddContact && (
          <AddContactModal
            onClose={() => setShowAddContact(false)}
            onAdd={addContact}
          />
        )}
      </AnimatePresence>

      {/* My Freedom ID Modal */}
      <AnimatePresence>
        {showMyId && (
          <FreedomIdModal
            freedomId={myFreedomId}
            publicKey={myPublicKey}
            onClose={() => setShowMyId(false)}
          />
        )}
      </AnimatePresence>
    </div>
  )
}

/**
 * Add Contact Modal
 */
function AddContactModal({ onClose, onAdd }) {
  const [publicKey, setPublicKey] = useState('')
  const [name, setName] = useState('')
  const [error, setError] = useState('')

  const handleAdd = async () => {
    if (!publicKey.trim()) {
      setError('Please enter their Freedom ID (public key)')
      return
    }
    if (publicKey.trim().length < 20) {
      setError('Invalid Freedom ID - too short')
      return
    }
    try {
      await onAdd(publicKey.trim(), name.trim() || 'Unknown')
      onClose()
    } catch {
      setError('Invalid Freedom ID format')
    }
  }

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4"
      onClick={onClose}
    >
      <motion.div
        initial={{ scale: 0.9, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        exit={{ scale: 0.9, opacity: 0 }}
        onClick={(e) => e.stopPropagation()}
        className="bg-slate-800 border border-slate-700/50 rounded-2xl p-6 w-full max-w-md"
      >
        <div className="flex items-center gap-3 mb-6">
          <div className="w-10 h-10 rounded-xl bg-green-500/20 flex items-center justify-center">
            <UserPlus className="w-5 h-5 text-green-400" />
          </div>
          <div>
            <h3 className="text-white font-bold">Add Secure Contact</h3>
            <p className="text-slate-400 text-xs">Paste their Freedom ID to connect</p>
          </div>
        </div>

        <div className="space-y-4">
          <div>
            <label className="text-slate-400 text-xs mb-1 block">Contact Name</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g., Alice"
              className="w-full bg-slate-900/50 text-white rounded-xl px-4 py-3 border border-slate-700/30 focus:border-green-500/50 focus:outline-none text-sm"
            />
          </div>
          <div>
            <label className="text-slate-400 text-xs mb-1 block">Freedom ID (Public Key)</label>
            <textarea
              value={publicKey}
              onChange={(e) => { setPublicKey(e.target.value); setError('') }}
              placeholder="Paste their full public key here..."
              rows={3}
              className="w-full bg-slate-900/50 text-white rounded-xl px-4 py-3 border border-slate-700/30 focus:border-green-500/50 focus:outline-none text-sm font-mono resize-none"
            />
          </div>

          {error && (
            <div className="flex items-center gap-2 text-red-400 text-xs">
              <AlertTriangle className="w-4 h-4" />
              {error}
            </div>
          )}

          <div className="flex gap-3">
            <button
              onClick={onClose}
              className="flex-1 py-3 rounded-xl border border-slate-600 text-slate-300 hover:bg-slate-700/50 transition-colors text-sm"
            >
              Cancel
            </button>
            <button
              onClick={handleAdd}
              className="flex-1 py-3 rounded-xl bg-green-500 hover:bg-green-600 text-white font-medium transition-colors text-sm"
            >
              Add Contact
            </button>
          </div>
        </div>
      </motion.div>
    </motion.div>
  )
}

/**
 * Freedom ID Modal - Shows your public key for sharing
 */
function FreedomIdModal({ freedomId, publicKey, onClose }) {
  const [copied, setCopied] = useState(false)

  const copyKey = () => {
    navigator.clipboard.writeText(publicKey)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4"
      onClick={onClose}
    >
      <motion.div
        initial={{ scale: 0.9, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        exit={{ scale: 0.9, opacity: 0 }}
        onClick={(e) => e.stopPropagation()}
        className="bg-slate-800 border border-slate-700/50 rounded-2xl p-6 w-full max-w-md"
      >
        <div className="text-center mb-6">
          <div className="w-16 h-16 rounded-full bg-gradient-to-br from-green-500/30 to-cyan-500/30 flex items-center justify-center mx-auto mb-4">
            <Fingerprint className="w-8 h-8 text-green-400" />
          </div>
          <h3 className="text-white font-bold text-lg">Your Freedom ID</h3>
          <p className="text-slate-400 text-xs mt-1">Share this with contacts so they can message you securely</p>
        </div>

        {/* Short ID */}
        <div className="bg-slate-900/50 rounded-xl p-4 text-center mb-4">
          <p className="text-slate-400 text-xs mb-1">Short ID</p>
          <p className="text-green-400 font-mono font-bold text-2xl tracking-widest">{freedomId}</p>
        </div>

        {/* Full Public Key */}
        <div className="bg-slate-900/50 rounded-xl p-4 mb-4">
          <p className="text-slate-400 text-xs mb-2">Full Public Key (share this)</p>
          <p className="text-slate-300 font-mono text-[10px] break-all leading-relaxed">{publicKey}</p>
        </div>

        <div className="flex gap-3">
          <button
            onClick={onClose}
            className="flex-1 py-3 rounded-xl border border-slate-600 text-slate-300 hover:bg-slate-700/50 transition-colors text-sm"
          >
            Close
          </button>
          <button
            onClick={copyKey}
            className="flex-1 py-3 rounded-xl bg-green-500 hover:bg-green-600 text-white font-medium transition-colors text-sm flex items-center justify-center gap-2"
          >
            {copied ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
            {copied ? 'Copied!' : 'Copy Key'}
          </button>
        </div>

        <p className="text-slate-500 text-[10px] text-center mt-4">
          Your private key never leaves this device. Only the public key is shared.
        </p>
      </motion.div>
    </motion.div>
  )
}

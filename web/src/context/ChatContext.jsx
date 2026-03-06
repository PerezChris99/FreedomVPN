import { createContext, useContext, useState, useEffect, useCallback } from 'react'
import messagingService, { DISAPPEAR_TIMES } from '../services/messagingService'

const ChatContext = createContext(null)

export function ChatProvider({ children }) {
  const [state, setState] = useState({
    isConnected: false,
    myFreedomId: null,
    myPublicKey: null,
    conversations: [],
    unreadCount: 0
  })
  const [activeChat, setActiveChat] = useState(null) // contactPublicKey
  const [isInitialized, setIsInitialized] = useState(false)

  useEffect(() => {
    const init = async () => {
      await messagingService.initialize()
      // Connect in local-only mode (no relay server needed for demo)
      messagingService.connectToRelay(null)
      setIsInitialized(true)
    }
    init()

    const unsub = messagingService.subscribe(setState)
    return unsub
  }, [])

  const sendMessage = useCallback(async (text) => {
    if (!activeChat || !text.trim()) return
    await messagingService.sendMessage(activeChat, text.trim())
  }, [activeChat])

  const addContact = useCallback(async (publicKey, name) => {
    await messagingService.addContact(publicKey, name)
  }, [])

  const deleteConversation = useCallback((contactKey) => {
    messagingService.deleteConversation(contactKey)
    if (activeChat === contactKey) setActiveChat(null)
  }, [activeChat])

  const deleteMessage = useCallback((contactKey, messageId) => {
    messagingService.deleteMessage(contactKey, messageId)
  }, [])

  const setDisappearTimer = useCallback((contactKey, timerKey) => {
    messagingService.setDisappearTimer(contactKey, timerKey)
  }, [])

  const getMessages = useCallback((contactKey) => {
    return messagingService.getMessages(contactKey)
  }, [])

  const wipeAll = useCallback(() => {
    messagingService.wipeAll()
    setActiveChat(null)
  }, [])

  const value = {
    ...state,
    isInitialized,
    activeChat,
    setActiveChat,
    sendMessage,
    addContact,
    deleteConversation,
    deleteMessage,
    setDisappearTimer,
    getMessages,
    wipeAll,
    DISAPPEAR_TIMES
  }

  return (
    <ChatContext.Provider value={value}>
      {children}
    </ChatContext.Provider>
  )
}

export function useChat() {
  const context = useContext(ChatContext)
  if (!context) throw new Error('useChat must be used within ChatProvider')
  return context
}

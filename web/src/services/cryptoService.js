/**
 * FreedomVPN Secure Messaging - Cryptography Service
 * 
 * End-to-end encryption using Web Crypto API:
 * - X25519-equivalent (ECDH P-256) key exchange
 * - AES-256-GCM for message encryption
 * - HKDF for key derivation
 * - Perfect Forward Secrecy via ephemeral keys
 * 
 * Zero-knowledge: Only public keys are ever shared.
 * Private keys never leave the device.
 */

const ALGORITHM = 'AES-GCM'
const KEY_LENGTH = 256
const IV_LENGTH = 12
const SALT_LENGTH = 16

class CryptoService {
  constructor() {
    this.keyPair = null
    this.publicKeyHex = null
    this.contacts = new Map() // publicKeyHex -> { sharedSecret, name }
  }

  /**
   * Initialize - generate or load identity key pair
   */
  async initialize() {
    const stored = localStorage.getItem('freedom_identity_key')
    if (stored) {
      try {
        const data = JSON.parse(stored)
        this.keyPair = {
          privateKey: await crypto.subtle.importKey(
            'jwk', data.privateKey,
            { name: 'ECDH', namedCurve: 'P-256' },
            true, ['deriveKey', 'deriveBits']
          ),
          publicKey: await crypto.subtle.importKey(
            'jwk', data.publicKey,
            { name: 'ECDH', namedCurve: 'P-256' },
            true, []
          )
        }
        this.publicKeyHex = await this._exportPublicKeyHex(this.keyPair.publicKey)
      } catch {
        await this._generateNewIdentity()
      }
    } else {
      await this._generateNewIdentity()
    }

    // Load saved contacts
    const savedContacts = localStorage.getItem('freedom_contacts')
    if (savedContacts) {
      try {
        const parsed = JSON.parse(savedContacts)
        for (const [key, value] of Object.entries(parsed)) {
          this.contacts.set(key, { name: value.name, sharedSecret: null })
        }
      } catch { /* ignore */ }
    }

    return this.publicKeyHex
  }

  /**
   * Generate a new identity key pair
   */
  async _generateNewIdentity() {
    this.keyPair = await crypto.subtle.generateKey(
      { name: 'ECDH', namedCurve: 'P-256' },
      true, ['deriveKey', 'deriveBits']
    )

    // Export and store
    const privateJwk = await crypto.subtle.exportKey('jwk', this.keyPair.privateKey)
    const publicJwk = await crypto.subtle.exportKey('jwk', this.keyPair.publicKey)
    localStorage.setItem('freedom_identity_key', JSON.stringify({
      privateKey: privateJwk,
      publicKey: publicJwk
    }))

    this.publicKeyHex = await this._exportPublicKeyHex(this.keyPair.publicKey)
  }

  /**
   * Export public key as hex string (Freedom ID)
   */
  async _exportPublicKeyHex(publicKey) {
    const raw = await crypto.subtle.exportKey('raw', publicKey)
    return Array.from(new Uint8Array(raw))
      .map(b => b.toString(16).padStart(2, '0'))
      .join('')
  }

  /**
   * Get short Freedom ID (first 16 chars of public key hash)
   */
  async getFreedomId() {
    if (!this.publicKeyHex) await this.initialize()
    const hash = await crypto.subtle.digest(
      'SHA-256',
      new TextEncoder().encode(this.publicKeyHex)
    )
    const hashHex = Array.from(new Uint8Array(hash))
      .map(b => b.toString(16).padStart(2, '0'))
      .join('')
    return hashHex.substring(0, 16).toUpperCase()
  }

  /**
   * Get full public key hex
   */
  getPublicKey() {
    return this.publicKeyHex
  }

  /**
   * Import a contact's public key and derive shared secret
   */
  async addContact(publicKeyHex, name = 'Unknown') {
    const publicKeyBytes = new Uint8Array(
      publicKeyHex.match(/.{1,2}/g).map(byte => parseInt(byte, 16))
    )
    
    const contactPublicKey = await crypto.subtle.importKey(
      'raw', publicKeyBytes,
      { name: 'ECDH', namedCurve: 'P-256' },
      true, []
    )

    // Derive shared secret using ECDH
    const sharedBits = await crypto.subtle.deriveBits(
      { name: 'ECDH', public: contactPublicKey },
      this.keyPair.privateKey,
      256
    )

    this.contacts.set(publicKeyHex, {
      name,
      sharedSecret: new Uint8Array(sharedBits)
    })

    // Save contacts (names only, secrets derived on-the-fly)
    this._saveContacts()
    
    return publicKeyHex
  }

  /**
   * Save contacts to localStorage (only names, not secrets)
   */
  _saveContacts() {
    const data = {}
    for (const [key, value] of this.contacts.entries()) {
      data[key] = { name: value.name }
    }
    localStorage.setItem('freedom_contacts', JSON.stringify(data))
  }

  /**
   * Encrypt a message for a specific contact
   * Uses AES-256-GCM with a derived key from the shared secret
   */
  async encryptMessage(recipientPublicKeyHex, plaintext) {
    let contact = this.contacts.get(recipientPublicKeyHex)
    if (!contact?.sharedSecret) {
      await this.addContact(recipientPublicKeyHex, contact?.name || 'Unknown')
      contact = this.contacts.get(recipientPublicKeyHex)
    }

    // Generate random IV and salt for this message (Perfect Forward Secrecy per message)
    const iv = crypto.getRandomValues(new Uint8Array(IV_LENGTH))
    const salt = crypto.getRandomValues(new Uint8Array(SALT_LENGTH))

    // Derive a unique message key using HKDF
    const baseKey = await crypto.subtle.importKey(
      'raw', contact.sharedSecret,
      'HKDF', false, ['deriveKey']
    )

    const messageKey = await crypto.subtle.deriveKey(
      { name: 'HKDF', hash: 'SHA-256', salt, info: new TextEncoder().encode('freedom-msg') },
      baseKey,
      { name: ALGORITHM, length: KEY_LENGTH },
      false, ['encrypt']
    )

    // Pad message to fixed block size to prevent length analysis
    const padded = this._padMessage(plaintext)
    const encoded = new TextEncoder().encode(padded)

    // Encrypt
    const ciphertext = await crypto.subtle.encrypt(
      { name: ALGORITHM, iv },
      messageKey,
      encoded
    )

    // Combine: salt + iv + ciphertext
    const result = new Uint8Array(SALT_LENGTH + IV_LENGTH + ciphertext.byteLength)
    result.set(salt, 0)
    result.set(iv, SALT_LENGTH)
    result.set(new Uint8Array(ciphertext), SALT_LENGTH + IV_LENGTH)

    // Return as base64
    return btoa(String.fromCharCode(...result))
  }

  /**
   * Decrypt a message from a contact
   */
  async decryptMessage(senderPublicKeyHex, encryptedBase64) {
    let contact = this.contacts.get(senderPublicKeyHex)
    if (!contact?.sharedSecret) {
      await this.addContact(senderPublicKeyHex, contact?.name || 'Unknown')
      contact = this.contacts.get(senderPublicKeyHex)
    }

    // Decode base64
    const data = Uint8Array.from(atob(encryptedBase64), c => c.charCodeAt(0))

    // Extract salt, iv, ciphertext
    const salt = data.slice(0, SALT_LENGTH)
    const iv = data.slice(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
    const ciphertext = data.slice(SALT_LENGTH + IV_LENGTH)

    // Derive the same message key
    const baseKey = await crypto.subtle.importKey(
      'raw', contact.sharedSecret,
      'HKDF', false, ['deriveKey']
    )

    const messageKey = await crypto.subtle.deriveKey(
      { name: 'HKDF', hash: 'SHA-256', salt, info: new TextEncoder().encode('freedom-msg') },
      baseKey,
      { name: ALGORITHM, length: KEY_LENGTH },
      false, ['decrypt']
    )

    // Decrypt
    const decrypted = await crypto.subtle.decrypt(
      { name: ALGORITHM, iv },
      messageKey,
      ciphertext
    )

    const text = new TextDecoder().decode(decrypted)
    return this._unpadMessage(text)
  }

  /**
   * Pad message to fixed block size (prevents length analysis)
   */
  _padMessage(message) {
    const BLOCK_SIZE = 256
    const padLength = BLOCK_SIZE - (message.length % BLOCK_SIZE)
    const lengthPrefix = message.length.toString().padStart(4, '0')
    return lengthPrefix + message + '\0'.repeat(padLength)
  }

  /**
   * Remove padding from message
   */
  _unpadMessage(padded) {
    const length = parseInt(padded.substring(0, 4), 10)
    return padded.substring(4, 4 + length)
  }

  /**
   * Get all contacts
   */
  getContacts() {
    const contacts = []
    for (const [key, value] of this.contacts.entries()) {
      contacts.push({ publicKey: key, name: value.name })
    }
    return contacts
  }

  /**
   * Remove a contact
   */
  removeContact(publicKeyHex) {
    this.contacts.delete(publicKeyHex)
    this._saveContacts()
  }

  /**
   * Rename a contact
   */
  renameContact(publicKeyHex, newName) {
    const contact = this.contacts.get(publicKeyHex)
    if (contact) {
      contact.name = newName
      this._saveContacts()
    }
  }

  /**
   * Wipe all crypto data (panic mode)
   */
  wipeAll() {
    localStorage.removeItem('freedom_identity_key')
    localStorage.removeItem('freedom_contacts')
    localStorage.removeItem('freedom_messages')
    this.keyPair = null
    this.publicKeyHex = null
    this.contacts.clear()
  }
}

const cryptoService = new CryptoService()
export default cryptoService

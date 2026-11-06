package com.example.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.UserProfile
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chatRepository: ChatRepository,
    val currentUserId: String,
    val currentUserName: String,
    val otherUserId: String
) : ViewModel() {

    private val _conversation = MutableStateFlow<Conversation?>(null)
    val conversation: StateFlow<Conversation?> = _conversation.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _otherUser = MutableStateFlow<UserProfile?>(null)
    val otherUser: StateFlow<UserProfile?> = _otherUser.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _replyTo = MutableStateFlow<Message?>(null)
    val replyTo: StateFlow<Message?> = _replyTo.asStateFlow()

    private val _editingMessage = MutableStateFlow<Message?>(null)
    val editingMessage: StateFlow<Message?> = _editingMessage.asStateFlow()

    // Filtered messages based on search query
    val displayMessages: StateFlow<List<Message>> = combine(_messages, _searchQuery) { msgs, query ->
        if (query.isBlank()) {
            msgs
        } else {
            msgs.filter { it.content.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        initializeConversation()
    }

    private fun initializeConversation() {
        viewModelScope.launch {
            // Observe other user profile
            launch {
                chatRepository.observeUserProfile(otherUserId).collect {
                    _otherUser.value = it
                }
            }

            // Get or create conversation
            val result = chatRepository.getOrCreateConversation(currentUserId, otherUserId)
            result.onSuccess { conv ->
                _conversation.value = conv

                // Observe conversation updates
                launch {
                    chatRepository.observeConversation(conv.id).collect {
                        _conversation.value = it
                    }
                }

                // Observe messages
                launch {
                    chatRepository.observeMessages(conv.id).collect { msgList ->
                        _messages.value = msgList
                        // Auto-mark unread messages
                        msgList.filter { !it.readBy.contains(currentUserId) && it.senderId != currentUserId }
                            .forEach { unread ->
                                chatRepository.markAsRead(conv.id, unread.id, currentUserId)
                            }
                    }
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSearch(active: Boolean) {
        _isSearching.value = active
        if (!active) _searchQuery.value = ""
    }

    fun setReplyTo(message: Message?) {
        _replyTo.value = message
    }

    fun setEditingMessage(message: Message?) {
        _editingMessage.value = message
    }

    fun sendMessage(content: String, type: String = "text", mediaUrl: String? = null, mediaName: String? = null, mediaSize: Long = 0L) {
        val conv = _conversation.value ?: return
        if (content.isBlank() && mediaUrl.isNullOrEmpty()) return

        val reply = _replyTo.value
        val editing = _editingMessage.value

        viewModelScope.launch {
            if (editing != null) {
                chatRepository.editMessage(conv.id, editing.id, content)
                _editingMessage.value = null
            } else {
                chatRepository.sendMessage(
                    conversationId = conv.id,
                    senderId = currentUserId,
                    senderName = currentUserName,
                    content = content,
                    type = type,
                    mediaUrl = mediaUrl,
                    mediaName = mediaName,
                    mediaSize = mediaSize,
                    replyToId = reply?.id,
                    replyToText = reply?.content?.take(80),
                    replyToSender = reply?.senderName
                )
                _replyTo.value = null
            }
        }
    }

    fun notifyScreenshot() {
        val conv = _conversation.value ?: return
        viewModelScope.launch {
            chatRepository.notifyScreenshotDetected(conv.id, currentUserId, currentUserName)
        }
    }

    fun deleteMessage(messageId: String) {
        val conv = _conversation.value ?: return
        viewModelScope.launch {
            chatRepository.deleteMessage(conv.id, messageId)
        }
    }

    fun toggleReaction(messageId: String, emoji: String) {
        val conv = _conversation.value ?: return
        viewModelScope.launch {
            chatRepository.toggleReaction(conv.id, messageId, emoji, currentUserId)
        }
    }

    fun togglePinMessage(messageId: String?) {
        val conv = _conversation.value ?: return
        viewModelScope.launch {
            val currentPin = conv.pinnedMessageId
            val newPin = if (currentPin == messageId) null else messageId
            chatRepository.togglePinMessage(conv.id, newPin)
        }
    }

    fun setTyping(isTyping: Boolean) {
        viewModelScope.launch {
            chatRepository.setTyping(currentUserId, isTyping)
        }
    }
}

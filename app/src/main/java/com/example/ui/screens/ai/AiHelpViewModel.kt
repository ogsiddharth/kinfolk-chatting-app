package com.example.ui.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AiHelpRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HelpMessage(
    val sender: String, // "user" or "assistant"
    val text: String
)

class AiHelpViewModel(
    private val aiHelpRepository: AiHelpRepository = AiHelpRepository()
) : ViewModel() {

    private val _messages = MutableStateFlow<List<HelpMessage>>(
        listOf(
            HelpMessage(
                sender = "assistant",
                text = "Hello! I am your Kinfolk Family Assistant. I can help answer questions about using Kinfolk, privacy settings, screenshot protection, invitations, or voice notes. What would you like to know?"
            )
        )
    )
    val messages: StateFlow<List<HelpMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val suggestedQuestions = listOf(
        "How do I invite someone to Kinfolk?",
        "How does screenshot detection work?",
        "Can I block screenshots completely?",
        "How do I record and send voice messages?",
        "What happens if I lose my password?"
    )

    fun askQuestion(question: String) {
        if (question.isBlank() || _isLoading.value) return

        val userMsg = HelpMessage(sender = "user", text = question.trim())
        _messages.value = _messages.value + userMsg

        viewModelScope.launch {
            _isLoading.value = true
            val result = aiHelpRepository.askAssistant(question)
            _isLoading.value = false
            result.onSuccess { answer ->
                _messages.value = _messages.value + HelpMessage(sender = "assistant", text = answer)
            }.onFailure { error ->
                _messages.value = _messages.value + HelpMessage(
                    sender = "assistant",
                    text = "I couldn't complete that request: ${error.localizedMessage ?: "Please check your network connection."}"
                )
            }
        }
    }
}

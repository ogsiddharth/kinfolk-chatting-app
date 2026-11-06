package com.example.ui.screens.chat

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Message
import com.example.ui.theme.BubbleReceived
import com.example.ui.theme.BubbleReceivedText
import com.example.ui.theme.BubbleSent
import com.example.ui.theme.BubbleSentText
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.SageSecondary
import com.example.util.AudioHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToSettings: () -> Unit,
    onOpenAiHelp: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    val conversation by viewModel.conversation.collectAsState()
    val messages by viewModel.displayMessages.collectAsState()
    val otherUser by viewModel.otherUser.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val replyTo by viewModel.replyTo.collectAsState()
    val editingMessage by viewModel.editingMessage.collectAsState()

    var textInput by remember { mutableStateOf("") }
    var selectedMessageForAction by remember { mutableStateOf<Message?>(null) }
    var selectedImageForPreview by remember { mutableStateOf<String?>(null) }
    var showAttachmentPicker by remember { mutableStateOf(false) }

    // Audio recording & playback state
    val audioHelper = remember { AudioHelper(context) }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var currentlyPlayingMessageId by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    // Setup Native Screenshot Detection (Android 14+) & FLAG_SECURE
    DisposableEffect(otherUser?.blockScreenshots) {
        val blockScreenshots = otherUser?.blockScreenshots == true
        if (activity != null) {
            if (blockScreenshots) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val screenCaptureCallback = Activity.ScreenCaptureCallback {
                    viewModel.notifyScreenshot()
                }
                activity.registerScreenCaptureCallback(context.mainExecutor, screenCaptureCallback)
                onDispose {
                    activity.unregisterScreenCaptureCallback(screenCaptureCallback)
                    audioHelper.stopPlayback()
                }
            } else {
                onDispose {
                    audioHelper.stopPlayback()
                }
            }
        } else {
            onDispose {
                audioHelper.stopPlayback()
            }
        }
    }

    // Update text field when editing message changes
    LaunchedEffect(editingMessage) {
        if (editingMessage != null) {
            textInput = editingMessage!!.content
        }
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Photo picker launcher (Zero-permission Android Photo Picker)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.sendMessage(
                content = "Shared a photo",
                type = "image",
                mediaUrl = uri.toString(),
                mediaName = "photo.jpg"
            )
        }
    }

    // Document picker launcher
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.sendMessage(
                content = "Shared a document",
                type = "document",
                mediaUrl = uri.toString(),
                mediaName = "document.pdf",
                mediaSize = 1024L * 150L
            )
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onNavigateToSettings() }
                        ) {
                            // Avatar
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(ForestPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = otherUser?.displayName?.take(1)?.uppercase() ?: "F",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = otherUser?.displayName ?: "Family Member",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                val showOnline = otherUser?.showLastSeen != false
                                val isOnline = otherUser?.isOnline == true && showOnline
                                val subtitle = when {
                                    isOnline -> "Active now"
                                    showOnline && otherUser?.lastSeen != null -> {
                                        val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault())
                                            .format(otherUser!!.lastSeen!!.toDate())
                                        "Last seen $timeStr"
                                    }
                                    else -> "Private status"
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isOnline) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF4CAF50))
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isOnline) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleSearch(!isSearching) }) {
                            Icon(
                                imageVector = if (isSearching) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        }
                        IconButton(onClick = onOpenAiHelp) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Assistant",
                                tint = Color(0xFFC4823F)
                            )
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Inline Search Bar
                AnimatedVisibility(visible = isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search messages...") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chat_search_input"),
                            shape = RoundedCornerShape(24.dp)
                        )
                    }
                }

                // Pinned Message Bar
                val pinnedId = conversation?.pinnedMessageId
                val pinnedMessage = messages.firstOrNull { it.id == pinnedId }
                if (pinnedMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable {
                                val idx = messages.indexOf(pinnedMessage)
                                if (idx >= 0) {
                                    coroutineScope.launch { listState.animateScrollToItem(idx) }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = ForestPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pinned Message",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestPrimary
                            )
                            Text(
                                text = pinnedMessage.content.take(60),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { viewModel.togglePinMessage(pinnedId) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Unpin", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            // Message Stream
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = SageSecondary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Your private family chat begins here.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Send a message, photo, or voice memo.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                items(messages, key = { it.id }) { message ->
                    MessageBubbleItem(
                        message = message,
                        currentUserId = viewModel.currentUserId,
                        onLongClick = { selectedMessageForAction = message },
                        onImageClick = { selectedImageForPreview = it },
                        onPlayAudio = { audioUrl ->
                            if (currentlyPlayingMessageId == message.id) {
                                audioHelper.stopPlayback()
                                currentlyPlayingMessageId = null
                            } else {
                                currentlyPlayingMessageId = message.id
                                audioHelper.playAudio(audioUrl) {
                                    currentlyPlayingMessageId = null
                                }
                            }
                        },
                        isPlayingAudio = currentlyPlayingMessageId == message.id,
                        onToggleReaction = { emoji ->
                            viewModel.toggleReaction(message.id, emoji)
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            // Reply or Edit Preview Bar
            if (replyTo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Replying to ${replyTo?.senderName}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = ForestPrimary
                        )
                        Text(
                            text = replyTo?.content?.take(60) ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { viewModel.setReplyTo(null) }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel reply")
                    }
                }
            }

            if (editingMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF3E0))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Editing message",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                        Text(
                            text = editingMessage?.content?.take(60) ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = {
                        viewModel.setEditingMessage(null)
                        textInput = ""
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel edit")
                    }
                }
            }

            // Bottom Input Composer
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Attachment Picker Button
                    IconButton(
                        onClick = { showAttachmentPicker = true },
                        modifier = Modifier.testTag("attach_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Attach",
                            tint = ForestPrimary
                        )
                    }

                    // Text Field
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = {
                            textInput = it
                            viewModel.setTyping(it.isNotBlank())
                        },
                        placeholder = { Text("Message...") },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp)
                            .testTag("message_input"),
                        maxLines = 4,
                        shape = RoundedCornerShape(20.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    // Voice Note or Send Button
                    if (textInput.isNotBlank() || editingMessage != null) {
                        IconButton(
                            onClick = {
                                viewModel.sendMessage(textInput)
                                textInput = ""
                                viewModel.setTyping(false)
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(ForestPrimary)
                                .testTag("send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        // Voice Memo Recorder Button
                        IconButton(
                            onClick = {
                                if (isRecordingAudio) {
                                    val recordedFile = audioHelper.stopRecording()
                                    isRecordingAudio = false
                                    if (recordedFile != null && recordedFile.exists()) {
                                        val base64Audio = audioHelper.fileToBase64(recordedFile)
                                        viewModel.sendMessage(
                                            content = "🎙️ Voice memo",
                                            type = "audio",
                                            mediaUrl = base64Audio,
                                            mediaName = "voice_memo.m4a"
                                        )
                                        Toast.makeText(context, "Voice memo sent!", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    val file = audioHelper.startRecording()
                                    if (file != null) {
                                        isRecordingAudio = true
                                        Toast.makeText(context, "Recording... tap mic to send", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Mic permission needed or device unavailable", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (isRecordingAudio) Color(0xFFC62828) else SageSecondary)
                                .testTag("mic_button")
                        ) {
                            Icon(
                                imageVector = if (isRecordingAudio) Icons.Default.Close else Icons.Default.Mic,
                                contentDescription = if (isRecordingAudio) "Stop Recording" else "Record Voice Memo",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Attachment Picker Bottom Sheet
    if (showAttachmentPicker) {
        AttachmentPickerBottomSheet(
            onDismiss = { showAttachmentPicker = false },
            onPickPhoto = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onPickDocument = {
                documentPickerLauncher.launch(arrayOf("application/pdf", "application/msword", "text/plain"))
            },
            onSendSampleFamilyPhoto = {
                viewModel.sendMessage(
                    content = "Family memory at home ❤️",
                    type = "image",
                    mediaUrl = "https://images.unsplash.com/photo-1511895426328-dc8714191300?w=600&auto=format&fit=crop",
                    mediaName = "family_memory.jpg"
                )
            }
        )
    }

    // Message Actions Bottom Sheet
    if (selectedMessageForAction != null) {
        val msg = selectedMessageForAction!!
        val isPinned = conversation?.pinnedMessageId == msg.id
        MessageActionBottomSheet(
            message = msg,
            currentUserId = viewModel.currentUserId,
            isPinned = isPinned,
            onDismiss = { selectedMessageForAction = null },
            onReact = { emoji ->
                viewModel.toggleReaction(msg.id, emoji)
            },
            onReply = {
                viewModel.setReplyTo(msg)
            },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Kinfolk Message", msg.content))
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            onEdit = {
                viewModel.setEditingMessage(msg)
            },
            onTogglePin = {
                viewModel.togglePinMessage(msg.id)
            },
            onDelete = {
                viewModel.deleteMessage(msg.id)
            }
        )
    }

    // Fullscreen Image Dialog
    if (selectedImageForPreview != null) {
        FullscreenImageViewer(
            imageUrl = selectedImageForPreview!!,
            caption = null,
            onDismiss = { selectedImageForPreview = null }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MessageBubbleItem(
    message: Message,
    currentUserId: String,
    onLongClick: () -> Unit,
    onImageClick: (String) -> Unit,
    onPlayAudio: (String) -> Unit,
    isPlayingAudio: Boolean,
    onToggleReaction: (String) -> Unit
) {
    val isMe = message.senderId == currentUserId

    if (message.type == "system_screenshot") {
        // System Screenshot Detection Alert
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFFF3E0))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFE65100),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message.content,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE65100)
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isMe) 18.dp else 4.dp,
                        bottomEnd = if (isMe) 4.dp else 18.dp
                    )
                )
                .background(if (isMe) BubbleSent else BubbleReceived)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                // Reply Quote
                if (!message.replyToText.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isMe) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Column {
                            Text(
                                text = message.replyToSender ?: "Reply",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMe) Color(0xFFD4E8DC) else ForestPrimary
                            )
                            Text(
                                text = message.replyToText,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isMe) BubbleSentText.copy(alpha = 0.8f) else BubbleReceivedText.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Media: Image
                if (message.type == "image" && !message.mediaUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = message.mediaUrl,
                        contentDescription = "Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onImageClick(message.mediaUrl) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Media: Audio / Voice Memo
                if (message.type == "audio" && !message.mediaUrl.isNullOrEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isMe) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.08f))
                            .clickable { onPlayAudio(message.mediaUrl) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isMe) Color.White else ForestPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlayingAudio) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlayingAudio) "Pause" else "Play",
                                tint = if (isMe) ForestPrimary else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isPlayingAudio) "Playing..." else "Voice Memo",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isMe) BubbleSentText else BubbleReceivedText
                            )
                            Text(
                                text = "Tap to listen",
                                fontSize = 10.sp,
                                color = if (isMe) BubbleSentText.copy(alpha = 0.7f) else BubbleReceivedText.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Text Content
                if (message.type == "text" || message.content.isNotBlank() && message.type != "image" && message.type != "audio") {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isMe) BubbleSentText else BubbleReceivedText
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Footer (time, edited, status ticks)
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isEdited) {
                        Text(
                            text = "edited ",
                            fontSize = 10.sp,
                            color = if (isMe) BubbleSentText.copy(alpha = 0.6f) else BubbleReceivedText.copy(alpha = 0.6f)
                        )
                    }

                    val timeStr = if (message.createdAt != null) {
                        SimpleDateFormat("h:mm a", Locale.getDefault()).format(message.createdAt.toDate())
                    } else "now"

                    Text(
                        text = timeStr,
                        fontSize = 10.sp,
                        color = if (isMe) BubbleSentText.copy(alpha = 0.7f) else BubbleReceivedText.copy(alpha = 0.7f)
                    )

                    if (isMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        val isRead = message.readBy.size > 1
                        Icon(
                            imageVector = if (isRead) Icons.Default.DoneAll else Icons.Default.Check,
                            contentDescription = if (isRead) "Read" else "Delivered",
                            tint = if (isRead) Color(0xFF81C784) else BubbleSentText.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Emoji Reaction Badges
        if (message.reactions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                message.reactions.forEach { (emoji, users) ->
                    if (users.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .clickable { onToggleReaction(emoji) }
                                .padding(horizontal = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = emoji, fontSize = 12.sp)
                            if (users.size > 1) {
                                Text(
                                    text = " ${users.size}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

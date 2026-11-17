package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.AppLanguage
import com.example.core.ImageCompressor
import com.example.core.LocalAppStrings
import com.example.core.TimeFormatter
import com.example.core.VideoThumbnailHelper
import com.example.core.VoiceNoteController
import com.example.data.model.ChatMessage
import com.example.data.model.DirectoryUser
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateView
import com.example.ui.components.UserAvatar
import com.example.ui.viewmodel.ChatRoomViewModel
import kotlinx.coroutines.launch
import java.io.File

private val quickReactions = listOf("❤️", "👍", "😂", "😮", "😢", "🙏")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    viewModel: ChatRoomViewModel,
    myProfile: UserProfile?,
    directoryUsers: List<DirectoryUser>,
    currentUserId: String,
    language: AppLanguage,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onOpenGroupInfo: (String) -> Unit,
    onStartCall: (peer: DirectoryUser, isVideo: Boolean) -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (showBackButton) {
        BackHandler(onBack = onBack)
    }

    val chat by viewModel.chatThread.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val replyingTo by viewModel.replyingTo.collectAsStateWithLifecycle()

    val directoryMap = remember(directoryUsers) { directoryUsers.associateBy { it.userId } }
    val listState = rememberLazyListState()

    val voiceController = remember(context) { VoiceNoteController(context, scope) }
    DisposableEffect(voiceController) {
        onDispose { voiceController.release() }
    }
    val isRecording by voiceController.isRecording.collectAsStateWithLifecycle()
    val recordingDurationMs by voiceController.recordingDurationMs.collectAsStateWithLifecycle()
    val amplitude by voiceController.amplitudeLevel.collectAsStateWithLifecycle()
    val playingMsgId by voiceController.playingMessageId.collectAsStateWithLifecycle()
    val playbackProgress by voiceController.playbackProgress.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var selectedMessageForAction by remember { mutableStateOf<ChatMessage?>(null) }
    var previewImageBase64 by remember { mutableStateOf<String?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val senderName = myProfile?.displayName?.ifBlank { "Me" } ?: "Me"

    // Mark chat read when opened or when new messages arrive
    LaunchedEffect(chat?.id, messagesState) {
        viewModel.markRead()
    }

    // Photo Picker for Image
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val base64 = ImageCompressor.compressUriToBase64(context, uri)
                if (base64 != null) {
                    viewModel.sendImage(senderName, "", base64)
                }
            }
        }
    }

    // Photo Picker for Video
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val preview = VideoThumbnailHelper.extractThumbnailAndDuration(context, uri)
                Toast.makeText(context, strings.videoTooLargeNotice, Toast.LENGTH_SHORT).show()
                viewModel.sendVideoPreview(senderName, "", preview.thumbnailBase64, preview.durationMs)
            }
        }
    }

    // Camera TakePicture
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            scope.launch {
                val base64 = ImageCompressor.compressUriToBase64(context, uri)
                if (base64 != null) {
                    viewModel.sendImage(senderName, "", base64)
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            Toast.makeText(context, strings.cameraPermissionRequired, Toast.LENGTH_SHORT).show()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceController.startRecording()
        } else {
            Toast.makeText(context, strings.micPermissionRequired, Toast.LENGTH_SHORT).show()
        }
    }

    val currentChat = chat
    val title = currentChat?.let { resolveChatTitle(it, currentUserId, directoryMap) } ?: "..."
    val peerId = currentChat?.participantIds?.firstOrNull { it != currentUserId }
    val peerUser = peerId?.let { directoryMap[it] }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.cancel)
                        }
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = currentChat?.isGroup == true) {
                                currentChat?.id?.let(onOpenGroupInfo)
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        UserAvatar(
                            name = title,
                            avatarUrl = if (currentChat?.isGroup == true) null else peerUser?.avatarUrl,
                            avatarBase64 = if (currentChat?.isGroup == true) currentChat.groupIconBase64 else peerUser?.avatarBase64,
                            size = 40.dp,
                            isOnline = currentChat?.isGroup == false && peerUser?.isOnline == true,
                            isGroup = currentChat?.isGroup == true
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subtitle = when {
                                currentChat?.isGroup == true -> "${currentChat.participantIds.size} ${strings.membersCountSuffix}"
                                peerUser?.isOnline == true -> strings.onlineNow
                                else -> strings.offline
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (peerUser?.isOnline == true && currentChat?.isGroup == false)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (currentChat?.isGroup == false && peerUser != null) {
                        IconButton(
                            onClick = { onStartCall(peerUser, false) },
                            modifier = Modifier.testTag("chat_audio_call_button")
                        ) {
                            Icon(Icons.Rounded.Call, contentDescription = strings.audioCall)
                        }
                        IconButton(
                            onClick = { onStartCall(peerUser, true) },
                            modifier = Modifier.testTag("chat_video_call_button")
                        ) {
                            Icon(Icons.Rounded.Videocam, contentDescription = strings.videoCall)
                        }
                    } else if (currentChat?.isGroup == true) {
                        IconButton(onClick = { onOpenGroupInfo(currentChat.id) }) {
                            Icon(Icons.Rounded.Group, contentDescription = strings.groupInfoTitle)
                        }
                    }
                    IconButton(onClick = {
                        showSearch = !showSearch
                        if (!showSearch) searchQuery = ""
                    }) {
                        Icon(Icons.Rounded.Search, contentDescription = strings.messageSearchHint)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .navigationBarsPadding()
        ) {
            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(strings.messageSearchHint) },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            searchQuery = ""
                            showSearch = false
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = strings.cancel)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            when (val state = messagesState) {
                UiState.Loading -> LoadingStateView(modifier = Modifier.weight(1f))
                is UiState.Error -> ErrorStateCard(message = state.message, modifier = Modifier.weight(1f))
                is UiState.Success -> {
                    val filteredMessages = remember(state.data, searchQuery) {
                        if (searchQuery.isBlank()) state.data
                        else state.data.filter { it.text.contains(searchQuery, ignoreCase = true) }
                    }

                    LaunchedEffect(filteredMessages.size) {
                        if (filteredMessages.isNotEmpty()) {
                            listState.animateScrollToItem(filteredMessages.lastIndex)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(filteredMessages, key = { it.id }) { msg ->
                            ChatMessageBubble(
                                message = msg,
                                isMine = msg.senderId == currentUserId,
                                isGroup = currentChat?.isGroup == true,
                                language = language,
                                isPlayingVoice = playingMsgId == msg.id,
                                voiceProgress = playbackProgress,
                                onToggleVoicePlay = { id, payload ->
                                    voiceController.togglePlayVoice(id, payload)
                                },
                                onImageClick = { b64 -> previewImageBase64 = b64 },
                                onLongPress = { selectedMessageForAction = msg }
                            )
                        }
                    }
                }
            }

            // Reply Banner
            AnimatedVisibility(visible = replyingTo != null) {
                replyingTo?.let { reply ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Reply,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${strings.replyLabel}: ${reply.senderName}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = reply.text.ifBlank { reply.type },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { viewModel.setReplyTarget(null) }) {
                                Icon(Icons.Rounded.Close, contentDescription = strings.cancel)
                            }
                        }
                    }
                }
            }

            // Composer Bar
            if (isRecording) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { voiceController.cancelRecording() }) {
                            Icon(Icons.Rounded.Delete, contentDescription = strings.cancel, tint = MaterialTheme.colorScheme.error)
                        }
                        Box(
                            modifier = Modifier
                                .size((12 + (amplitude * 10)).dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${strings.recordingVoice} ${TimeFormatter.formatDuration(recordingDurationMs)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = strings.tapToStopSend,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledIconButton(
                            onClick = {
                                val recorded = voiceController.stopAndEncodeRecording()
                                if (recorded != null) {
                                    viewModel.sendVoiceNote(senderName, recorded.first, recorded.second)
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Rounded.Stop, contentDescription = strings.send)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text(strings.typeMessageHint) },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        trailingIcon = {
                            IconButton(onClick = { showAttachmentSheet = true }) {
                                Icon(Icons.Rounded.AttachFile, contentDescription = strings.attachmentPhoto)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("message_input")
                    )

                    if (inputText.isNotBlank()) {
                        FilledIconButton(
                            onClick = {
                                val textToSend = inputText
                                inputText = ""
                                viewModel.sendText(senderName, textToSend)
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .size(52.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("send_message_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = strings.send)
                        }
                    } else {
                        FilledIconButton(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    voiceController.startRecording()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .size(52.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("record_voice_button")
                        ) {
                            Icon(Icons.Rounded.Mic, contentDescription = strings.voiceMessageLabel)
                        }
                    }
                }
            }
        }
    }

    if (showAttachmentSheet) {
        ModalBottomSheet(onDismissRequest = { showAttachmentSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AttachmentOptionRow(
                    icon = Icons.Rounded.PhotoLibrary,
                    label = strings.attachmentPhoto,
                    onClick = {
                        showAttachmentSheet = false
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                AttachmentOptionRow(
                    icon = Icons.Rounded.VideoLibrary,
                    label = strings.attachmentVideo,
                    onClick = {
                        showAttachmentSheet = false
                        videoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    }
                )
                AttachmentOptionRow(
                    icon = Icons.Rounded.PhotoCamera,
                    label = strings.attachmentCamera,
                    onClick = {
                        showAttachmentSheet = false
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            val file = File(context.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            pendingCameraUri = uri
                            takePictureLauncher.launch(uri)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    selectedMessageForAction?.let { msg ->
        ModalBottomSheet(onDismissRequest = { selectedMessageForAction = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    quickReactions.forEach { emoji ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .clickable {
                                    viewModel.toggleReaction(msg, emoji)
                                    selectedMessageForAction = null
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(emoji, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }

                AttachmentOptionRow(
                    icon = Icons.AutoMirrored.Rounded.Reply,
                    label = strings.reply,
                    onClick = {
                        viewModel.setReplyTarget(msg)
                        selectedMessageForAction = null
                    }
                )
                if (msg.text.isNotBlank()) {
                    AttachmentOptionRow(
                        icon = Icons.Rounded.ContentCopy,
                        label = strings.copy,
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Songjog", msg.text))
                            Toast.makeText(context, strings.copiedToast, Toast.LENGTH_SHORT).show()
                            selectedMessageForAction = null
                        }
                    )
                }
                if (msg.senderId == currentUserId) {
                    AttachmentOptionRow(
                        icon = Icons.Rounded.Delete,
                        label = strings.delete,
                        tint = MaterialTheme.colorScheme.error,
                        onClick = {
                            viewModel.deleteMessage(msg.id)
                            selectedMessageForAction = null
                        }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    previewImageBase64?.let { base64 ->
        val bmp = remember(base64) { ImageCompressor.decodeBase64ToImageBitmap(base64) }
        if (bmp != null) {
            Dialog(onDismissRequest = { previewImageBase64 = null }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black)
                        .clickable { previewImageBase64 = null }
                ) {
                    Image(
                        bitmap = bmp,
                        contentDescription = strings.photoMessageLabel,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

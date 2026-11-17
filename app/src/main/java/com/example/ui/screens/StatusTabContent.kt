package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.AppLanguage
import com.example.core.ImageCompressor
import com.example.core.LocalAppStrings
import com.example.core.TimeFormatter
import com.example.data.model.StatusStory
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateView
import com.example.ui.components.UserAvatar
import com.example.ui.components.parseHexColor
import kotlinx.coroutines.launch

private val statusPalette = listOf(
    "#0B7A5C",
    "#1E3A8A",
    "#7C3AED",
    "#BE123C",
    "#B45309",
    "#0F172A"
)

@Composable
fun StatusTabContent(
    statusesState: UiState<List<StatusStory>>,
    myProfile: UserProfile?,
    currentUserId: String,
    language: AppLanguage,
    onPostTextStatus: (text: String, bgHex: String) -> Unit,
    onPostImageStatus: (caption: String, imageBase64: String) -> Unit,
    onOpenStatusViewer: (ownerUserId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showTextComposer by remember { mutableStateOf(false) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingPhotoUri = uri
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        when (statusesState) {
            UiState.Loading -> LoadingStateView()
            is UiState.Error -> ErrorStateCard(message = statusesState.message)
            is UiState.Success -> {
                val allStatuses = statusesState.data
                val myStatuses = remember(allStatuses, currentUserId) {
                    allStatuses.filter { it.userId == currentUserId }
                }
                val groupedOthers = remember(allStatuses, currentUserId) {
                    allStatuses.filter { it.userId != currentUserId }
                        .groupBy { it.userId }
                        .values
                        .toList()
                }

                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // My Status Card
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            if (myStatuses.isNotEmpty()) {
                                                onOpenStatusViewer(currentUserId)
                                            } else {
                                                showTextComposer = true
                                            }
                                        }
                                        .padding(4.dp)
                                ) {
                                    Box {
                                        UserAvatar(
                                            name = myProfile?.displayName ?: "Me",
                                            avatarUrl = myProfile?.avatarUrl,
                                            avatarBase64 = myProfile?.avatarBase64,
                                            size = 56.dp,
                                            modifier = if (myStatuses.isNotEmpty()) {
                                                Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                            } else Modifier
                                        )
                                        if (myStatuses.isEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .align(Alignment.BottomEnd)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Add,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = strings.myStatusTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (myStatuses.isNotEmpty()) {
                                                "${myStatuses.size} • ${TimeFormatter.formatChatListDate(myStatuses.first().createdAt, language.code, strings.justNow)}"
                                            } else {
                                                strings.tapToAddStatus
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    FilledTonalButton(
                                        onClick = { showTextComposer = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("add_text_status_button")
                                    ) {
                                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.textStatusButton)
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            photoPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("add_photo_status_button")
                                    ) {
                                        Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.photoStatusButton)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = strings.recentUpdates,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                        )
                    }

                    if (groupedOthers.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = strings.noStatusesYet,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(groupedOthers, key = { it.first().userId }) { userStatuses ->
                            val latest = userStatuses.first()
                            val hasUnviewed = userStatuses.any { currentUserId !in it.viewerIds }
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { onOpenStatusViewer(latest.userId) }
                                    .minimumInteractiveComponentSize()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(58.dp)
                                            .clip(CircleShape)
                                            .border(
                                                width = 2.5.dp,
                                                color = if (hasUnviewed) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                                shape = CircleShape
                                            )
                                            .padding(3.dp)
                                    ) {
                                        UserAvatar(
                                            name = latest.userName,
                                            avatarUrl = latest.userAvatar,
                                            size = 52.dp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = latest.userName,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = TimeFormatter.formatChatListDate(
                                                latest.createdAt,
                                                language.code,
                                                strings.justNow
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
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
    }

    if (showTextComposer) {
        TextStatusComposerDialog(
            onDismiss = { showTextComposer = false },
            onPost = { text, hex ->
                showTextComposer = false
                onPostTextStatus(text, hex)
            }
        )
    }

    pendingPhotoUri?.let { uri ->
        PhotoStatusCaptionDialog(
            onDismiss = { pendingPhotoUri = null },
            onPost = { caption ->
                val targetUri = uri
                pendingPhotoUri = null
                scope.launch {
                    val base64 = ImageCompressor.compressUriToBase64(context, targetUri)
                    if (base64 != null) {
                        onPostImageStatus(caption, base64)
                    }
                }
            }
        )
    }
}

@Composable
private fun TextStatusComposerDialog(
    onDismiss: () -> Unit,
    onPost: (String, String) -> Unit
) {
    val strings = LocalAppStrings.current
    var text by remember { mutableStateOf("") }
    var selectedHex by remember { mutableStateOf(statusPalette.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.textStatusButton) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(parseHexColor(selectedHex)),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { if (it.length <= 300) text = it },
                        placeholder = {
                            Text(
                                strings.statusTextHint,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    statusPalette.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(hex))
                                .border(
                                    width = if (hex == selectedHex) 3.dp else 1.dp,
                                    color = if (hex == selectedHex) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                                .clickable { selectedHex = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onPost(text.trim(), selectedHex) },
                enabled = text.isNotBlank()
            ) {
                Text(strings.postStatus)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
private fun PhotoStatusCaptionDialog(
    onDismiss: () -> Unit,
    onPost: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    var caption by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.photoStatusButton) },
        text = {
            OutlinedTextField(
                value = caption,
                onValueChange = { if (it.length <= 200) caption = it },
                placeholder = { Text(strings.statusTextHint) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onPost(caption.trim()) }) {
                Text(strings.postStatus)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

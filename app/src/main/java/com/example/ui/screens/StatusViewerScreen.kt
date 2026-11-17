package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.AppLanguage
import com.example.core.ImageCompressor
import com.example.core.LocalAppStrings
import com.example.core.TimeFormatter
import com.example.data.model.StatusStory
import com.example.ui.components.UserAvatar
import com.example.ui.components.parseHexColor
import kotlinx.coroutines.delay

@Composable
fun StatusViewerScreen(
    statuses: List<StatusStory>,
    currentUserId: String,
    language: AppLanguage,
    onMarkViewed: (StatusStory) -> Unit,
    onDeleteStatus: (String) -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    BackHandler(onBack = onBack)

    if (statuses.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var index by remember { mutableIntStateOf(0) }
    val safeIndex = index.coerceIn(0, statuses.lastIndex)
    val active = statuses[safeIndex]

    LaunchedEffect(active.id) {
        onMarkViewed(active)
        delay(5000L)
        if (safeIndex < statuses.lastIndex) {
            index = safeIndex + 1
        } else {
            onBack()
        }
    }

    val bgColor = if (active.type == "image") Color(0xFF0A0F0D)
    else parseHexColor(active.backgroundHex)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .pointerInput(safeIndex, statuses.size) {
                detectTapGestures { offset ->
                    if (offset.x < size.width / 3f) {
                        if (safeIndex > 0) index = safeIndex - 1
                    } else {
                        if (safeIndex < statuses.lastIndex) index = safeIndex + 1 else onBack()
                    }
                }
            }
            .safeDrawingPadding()
    ) {
        // Content
        if (active.type == "image") {
            val bitmap = remember(active.imageBase64) {
                ImageCompressor.decodeBase64ToImageBitmap(active.imageBase64)
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = active.text,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (active.text.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp, vertical = 72.dp)
                ) {
                    Text(
                        text = active.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        } else {
            Text(
                text = active.text,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
            )
        }

        // Top Progress + Author Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                statuses.forEachIndexed { idx, _ ->
                    LinearProgressIndicator(
                        progress = { if (idx <= safeIndex) 1f else 0.25f },
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = strings.cancel,
                        tint = Color.White
                    )
                }
                UserAvatar(
                    name = active.userName,
                    avatarUrl = active.userAvatar,
                    size = 40.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = active.userName,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = TimeFormatter.formatClockTime(active.createdAt, language.code),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                if (active.userId == currentUserId) {
                    IconButton(onClick = {
                        onDeleteStatus(active.id)
                        if (statuses.size <= 1) onBack()
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = strings.delete,
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Viewer count pill at bottom for owner
        if (active.userId == currentUserId) {
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Visibility,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${strings.viewedByLabel}: ${active.viewerIds.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

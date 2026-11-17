package com.example.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ImageCompressor {
    private val bitmapCache = LruCache<String, ImageBitmap>(40)

    suspend fun compressUriToBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = 720,
        quality: Int = 65
    ): String? = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            compressBytesToBase64(bytes, maxDimension, quality)
        } catch (_: Exception) {
            null
        }
    }

    fun compressBytesToBase64(
        bytes: ByteArray,
        maxDimension: Int = 720,
        quality: Int = 65
    ): String? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
            var sampleSize = 1
            while (boundsOpts.outWidth / sampleSize > maxDimension * 2 ||
                boundsOpts.outHeight / sampleSize > maxDimension * 2
            ) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                ?: return null
            val scale = minOf(
                maxDimension.toFloat() / decoded.width.coerceAtLeast(1),
                maxDimension.toFloat() / decoded.height.coerceAtLeast(1),
                1f
            )
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true
                ).also { if (it !== decoded) decoded.recycle() }
            } else {
                decoded
            }
            var q = quality
            var encodedBytes: ByteArray
            do {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, q, out)
                encodedBytes = out.toByteArray()
                q -= 10
            } while (encodedBytes.size > 260_000 && q >= 25)
            scaled.recycle()
            Base64.encodeToString(encodedBytes, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    fun decodeBase64ToImageBitmap(base64: String?): ImageBitmap? {
        if (base64.isNullOrBlank()) return null
        val key = base64.take(48) + "_" + base64.length
        bitmapCache.get(key)?.let { return it }
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            bmp.asImageBitmap().also { bitmapCache.put(key, it) }
        } catch (_: Exception) {
            null
        }
    }
}

data class VideoPreviewResult(
    val thumbnailBase64: String?,
    val durationMs: Long
)

object VideoThumbnailHelper {
    suspend fun extractThumbnailAndDuration(context: Context, uri: Uri): VideoPreviewResult =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val durationMs = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                val base64 = if (frame != null) {
                    val out = ByteArrayOutputStream()
                    frame.compress(Bitmap.CompressFormat.JPEG, 60, out)
                    frame.recycle()
                    ImageCompressor.compressBytesToBase64(out.toByteArray(), 540, 60)
                } else null
                VideoPreviewResult(base64, durationMs)
            } catch (_: Exception) {
                VideoPreviewResult(null, 0L)
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
        }
}

class VoiceNoteController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStartMs: Long = 0L
    private var meterJob: Job? = null

    private var player: MediaPlayer? = null
    private var playbackJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    private val _amplitudeLevel = MutableStateFlow(0f)
    val amplitudeLevel: StateFlow<Float> = _amplitudeLevel.asStateFlow()

    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    fun startRecording(): Boolean {
        stopPlayback()
        return try {
            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(16_000)
            mr.setAudioSamplingRate(16_000)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()

            recorder = mr
            recordingFile = file
            recordingStartMs = System.currentTimeMillis()
            _isRecording.value = true
            _recordingDurationMs.value = 0L

            meterJob?.cancel()
            meterJob = scope.launch {
                while (isActive && _isRecording.value) {
                    _recordingDurationMs.value = System.currentTimeMillis() - recordingStartMs
                    val amp = try {
                        (recorder?.maxAmplitude ?: 0) / 32767f
                    } catch (_: Exception) {
                        0f
                    }
                    _amplitudeLevel.value = amp.coerceIn(0.08f, 1f)
                    if (_recordingDurationMs.value >= 120_000L) {
                        break
                    }
                    delay(80)
                }
            }
            true
        } catch (_: Exception) {
            cancelRecording()
            false
        }
    }

    fun stopAndEncodeRecording(): Pair<String, Long>? {
        meterJob?.cancel()
        val duration = (System.currentTimeMillis() - recordingStartMs).coerceAtLeast(500L)
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            _isRecording.value = false
            _amplitudeLevel.value = 0f

            val file = recordingFile ?: return null
            val bytes = file.readBytes()
            file.delete()
            recordingFile = null
            if (bytes.isEmpty() || bytes.size > 320_000) return null
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            base64 to duration
        } catch (_: Exception) {
            cancelRecording()
            null
        }
    }

    fun cancelRecording() {
        meterJob?.cancel()
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        recordingFile?.delete()
        recordingFile = null
        _isRecording.value = false
        _recordingDurationMs.value = 0L
        _amplitudeLevel.value = 0f
    }

    fun togglePlayVoice(messageId: String, base64Audio: String) {
        if (_playingMessageId.value == messageId) {
            stopPlayback()
            return
        }
        stopPlayback()
        try {
            val bytes = Base64.decode(base64Audio, Base64.DEFAULT)
            val tempFile = File(context.cacheDir, "play_${messageId.take(24)}.m4a")
            tempFile.writeBytes(bytes)
            val mp = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                start()
            }
            player = mp
            _playingMessageId.value = messageId
            _playbackProgress.value = 0f
            mp.setOnCompletionListener {
                stopPlayback()
                tempFile.delete()
            }
            playbackJob?.cancel()
            playbackJob = scope.launch {
                while (isActive && _playingMessageId.value == messageId) {
                    val dur = player?.duration?.takeIf { it > 0 } ?: 1
                    val pos = player?.currentPosition ?: 0
                    _playbackProgress.value = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    delay(80)
                }
            }
        } catch (_: Exception) {
            stopPlayback()
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        _playingMessageId.value = null
        _playbackProgress.value = 0f
    }

    fun release() {
        cancelRecording()
        stopPlayback()
    }
}

object TimeFormatter {
    fun formatClockTime(timestamp: Timestamp?, localeCode: String): String {
        val date = timestamp?.toDate() ?: Date()
        val locale = if (localeCode == "bn") Locale("bn", "BD") else Locale.ENGLISH
        return SimpleDateFormat("h:mm a", locale).format(date)
    }

    fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.ENGLISH, "%d:%02d", min, sec)
    }

    fun formatSeconds(seconds: Long): String {
        val min = seconds / 60
        val sec = seconds % 60
        return String.format(Locale.ENGLISH, "%02d:%02d", min, sec)
    }

    fun formatChatListDate(timestamp: Timestamp?, localeCode: String, justNowLabel: String): String {
        val date = timestamp?.toDate() ?: return ""
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { time = date }
        val diffMs = now.timeInMillis - target.timeInMillis
        if (diffMs in 0..60_000) return justNowLabel
        val locale = if (localeCode == "bn") Locale("bn", "BD") else Locale.ENGLISH
        return if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
        ) {
            SimpleDateFormat("h:mm a", locale).format(date)
        } else {
            SimpleDateFormat("dd MMM", locale).format(date)
        }
    }
}

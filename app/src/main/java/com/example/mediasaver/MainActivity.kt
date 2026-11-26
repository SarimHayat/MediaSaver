package com.example.mediasaver

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class MediaMode(val label: String) {
    VIDEO("Video (MP4)"),
    AUDIO("Audio Only")
}

enum class VideoQuality(val label: String, val formatString: String) {
    BEST("Best", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best"),
    Q1080("1080p", "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=1080]+bestaudio/best[height<=1080]"),
    Q720("720p", "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=720]+bestaudio/best[height<=720]"),
    Q480("480p", "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=480]+bestaudio/best[height<=480]"),
    Q360("360p", "bestvideo[height<=360][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=360]+bestaudio/best[height<=360]")
}

enum class AudioQuality(val label: String, val format: String, val quality: String) {
    MP3_HIGH("MP3 320k", "mp3", "0"),
    MP3_MED("MP3 128k", "mp3", "5"),
    M4A_BEST("M4A Best", "m4a", "0")
}

enum class PlatformInfo(val label: String, val accent: Color) {
    YOUTUBE("YouTube", Color(0xFFFF3B5C)),
    TIKTOK("TikTok", Color(0xFF00F2EA)),
    INSTAGRAM("Instagram Reels", Color(0xFFE1306C)),
    OTHER("Media Link", Color(0xFF7C6BFF)),
    NONE("", Color(0xFF7C6BFF))
}

class MainActivity : ComponentActivity() {
    private var sharedUrlState = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0B0D14),
                    surface = Color(0xFF151824),
                    primary = Color(0xFF7C6BFF)
                )
            ) {
                MediaSaverScreen(
                    incomingUrl = sharedUrlState.value,
                    onConsumeIncomingUrl = { sharedUrlState.value = "" }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            val extracted = extractUrl(sharedText)
            if (extracted.isNotBlank()) {
                sharedUrlState.value = extracted
            }
        }
    }
}

fun extractUrl(text: String): String {
    val match = Regex("https?://[^\\s]+").find(text)
    return match?.value?.trim() ?: text.trim()
}

fun detectPlatform(url: String): PlatformInfo {
    val lower = url.lowercase()
    return when {
        lower.isBlank() -> PlatformInfo.NONE
        "youtube.com" in lower || "youtu.be" in lower -> PlatformInfo.YOUTUBE
        "tiktok.com" in lower -> PlatformInfo.TIKTOK
        "instagram.com" in lower -> PlatformInfo.INSTAGRAM
        lower.startsWith("http") -> PlatformInfo.OTHER
        else -> PlatformInfo.NONE
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaSaverScreen(
    incomingUrl: String,
    onConsumeIncomingUrl: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var urlText by remember { mutableStateOf("") }
    var mediaMode by remember { mutableStateOf(MediaMode.VIDEO) }
    var videoQuality by remember { mutableStateOf(VideoQuality.Q1080) }
    var audioQuality by remember { mutableStateOf(AudioQuality.MP3_HIGH) }

    var engineReady by remember { mutableStateOf(false) }
    var isUpdatingEngine by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var statusMessage by remember { mutableStateOf("Initializing downloader engine...") }
    var savedSuccess by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val processId = remember { "MediaSaverProcess" }
    val platform = remember(urlText) { detectPlatform(urlText) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(incomingUrl) {
        if (incomingUrl.isNotBlank()) {
            urlText = incomingUrl
            savedSuccess = false
            errorMessage = null
            onConsumeIncomingUrl()
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(context.applicationContext)
                FFmpeg.getInstance().init(context.applicationContext)
                withContext(Dispatchers.Main) {
                    engineReady = true
                    statusMessage = "Ready to save"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Engine init failed: ${e.localizedMessage}"
                }
            }
        }
    }

    val accentColor by animateColorAsState(
        targetValue = platform.accent,
        animationSpec = tween(500),
        label = "accent"
    )
    val animatedProgress by animateFloatAsState(
        targetValue = downloadProgress.coerceIn(0f, 1f),
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "progress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "aurora")
    val drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C12))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accentColor.copy(alpha = 0.24f), Color.Transparent),
                    center = Offset(size.width * (0.2f + drift * 0.5f), size.height * 0.14f),
                    radius = size.width * 0.85f
                ),
                radius = size.width * 0.85f,
                center = Offset(size.width * (0.2f + drift * 0.5f), size.height * 0.14f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF3B82F6).copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(size.width * (0.85f - drift * 0.35f), size.height * 0.8f),
                    radius = size.width * 0.9f
                ),
                radius = size.width * 0.9f,
                center = Offset(size.width * (0.85f - drift * 0.35f), size.height * 0.8f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MediaSaver",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "TikTok • YouTube • IG Reels",
                        fontSize = 13.sp,
                        color = Color(0xFF9AA3B8)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF1A1E2E),
                    modifier = Modifier
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = engineReady && !isUpdatingEngine && !isDownloading) {
                            scope.launch {
                                isUpdatingEngine = true
                                errorMessage = null
                                statusMessage = "Updating yt-dlp engine..."
                                withContext(Dispatchers.IO) {
                                    try {
                                        val status = YoutubeDL.getInstance().updateYoutubeDL(
                                            context.applicationContext,
                                            YoutubeDL.UpdateChannel.NIGHTLY
                                        )
                                        withContext(Dispatchers.Main) {
                                            statusMessage = "Engine status: $status"
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            errorMessage = "Update failed: ${e.localizedMessage}"
                                        }
                                    } finally {
                                        withContext(Dispatchers.Main) {
                                            isUpdatingEngine = false
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isUpdatingEngine) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = accentColor
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Update Engine",
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = if (isUpdatingEngine) "Updating..." else "Update Engine",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF141824).copy(alpha = 0.92f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (platform != PlatformInfo.NONE) accentColor.copy(alpha = 0.55f)
                        else Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Paste Video Link",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFB4BCD0)
                        )

                        AnimatedVisibility(
                            visible = platform != PlatformInfo.NONE,
                            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                            exit = scaleOut() + fadeOut()
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(accentColor.copy(alpha = 0.18f))
                                    .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = platform.label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0C0F18))
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (urlText.isEmpty()) {
                                Text(
                                    text = "https://youtube.com/... or tiktok / ig",
                                    color = Color(0xFF565E73),
                                    fontSize = 14.sp
                                )
                            }
                            BasicTextField(
                                value = urlText,
                                onValueChange = {
                                    urlText = it
                                    savedSuccess = false
                                    errorMessage = null
                                },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                                cursorBrush = SolidColor(accentColor),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        AnimatedVisibility(visible = urlText.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    urlText = ""
                                    savedSuccess = false
                                    errorMessage = null
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = "Clear",
                                    tint = Color(0xFF8C96AD),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = accentColor.copy(alpha = 0.16f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val clip = clipboardManager.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        urlText = extractUrl(clip)
                                        savedSuccess = false
                                        errorMessage = null
                                    }
                                }
                        ) {
                            Text(
                                text = "Paste from Clipboard",
                                color = accentColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF141824).copy(alpha = 0.92f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0C0F18))
                            .padding(4.dp)
                    ) {
                        MediaMode.entries.forEach { mode ->
                            val selected = mediaMode == mode
                            val chipBg by animateColorAsState(
                                targetValue = if (selected) accentColor else Color.Transparent,
                                animationSpec = tween(250),
                                label = "modeBg"
                            )
                            val textColor by animateColorAsState(
                                targetValue = if (selected) Color(0xFF0B0D14) else Color(0xFF9AA3B8),
                                animationSpec = tween(250),
                                label = "modeText"
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(chipBg)
                                    .clickable(enabled = !isDownloading) { mediaMode = mode }
                                    .padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                            }
                        }
                    }

                    Text(
                        text = "Select Quality",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF9AA3B8)
                    )

                    AnimatedContent(
                        targetState = mediaMode,
                        transitionSpec = {
                            (fadeIn(tween(220)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(150))
                        },
                        label = "qualityChips"
                    ) { mode ->
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (mode == MediaMode.VIDEO) {
                                VideoQuality.entries.forEach { q ->
                                    QualityChip(
                                        label = q.label,
                                        selected = videoQuality == q,
                                        accentColor = accentColor,
                                        enabled = !isDownloading,
                                        onClick = { videoQuality = q }
                                    )
                                }
                            } else {
                                AudioQuality.entries.forEach { q ->
                                    QualityChip(
                                        label = q.label,
                                        selected = audioQuality == q,
                                        accentColor = accentColor,
                                        enabled = !isDownloading,
                                        onClick = { audioQuality = q }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isDownloading || savedSuccess || errorMessage != null || !engineReady,
                enter = fadeIn() + scaleIn(initialScale = 0.95f),
                exit = fadeOut() + scaleOut(targetScale = 0.95f)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF141824),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            when {
                                errorMessage != null -> Color(0xFFFF4D6D).copy(alpha = 0.5f)
                                savedSuccess -> Color(0xFF22C55E).copy(alpha = 0.55f)
                                else -> accentColor.copy(alpha = 0.35f)
                            },
                            RoundedCornerShape(20.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (savedSuccess) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF22C55E)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = errorMessage ?: statusMessage,
                                    color = if (errorMessage != null) Color(0xFFFF6B81) else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (isDownloading) {
                                Text(
                                    text = "${(animatedProgress * 100).toInt()}%",
                                    color = accentColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (isDownloading) {
                            if (downloadProgress <= 0f) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(50)),
                                    color = accentColor,
                                    trackColor = Color(0xFF22283A)
                                )
                            } else {
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(50)),
                                    color = accentColor,
                                    trackColor = Color(0xFF22283A),
                                    strokeCap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val buttonScale by animateFloatAsState(
                targetValue = if (isPressed) 0.96f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "btnScale"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .scale(buttonScale)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(accentColor, Color(0xFF6366F1))
                            )
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = engineReady && !isDownloading && !isUpdatingEngine && urlText.isNotBlank()
                        ) {
                            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                return@clickable
                            }

                            scope.launch {
                                isDownloading = true
                                savedSuccess = false
                                errorMessage = null
                                downloadProgress = 0f
                                statusMessage = "Fetching media info..."

                                val result = downloadAndSaveToGallery(
                                    context = context,
                                    url = urlText.trim(),
                                    mediaMode = mediaMode,
                                    videoQuality = videoQuality,
                                    audioQuality = audioQuality,
                                    processId = processId,
                                    onProgress = { pct, line ->
                                        scope.launch(Dispatchers.Main) {
                                            if (pct >= 0f) downloadProgress = pct / 100f
                                            statusMessage = when {
                                                line.contains("Merger", ignoreCase = true) ||
                                                    line.contains("ffmpeg", ignoreCase = true) ->
                                                    "Merging high-quality streams..."
                                                pct > 0f -> "Downloading media..."
                                                else -> "Connecting..."
                                            }
                                        }
                                    }
                                )

                                isDownloading = false
                                result.onSuccess { savedName ->
                                    downloadProgress = 1f
                                    savedSuccess = true
                                    statusMessage = "Saved to Gallery: $savedName"
                                }.onFailure { err ->
                                    errorMessage = err.localizedMessage ?: "Download failed"
                                }
                            }
                        }
                        .padding(vertical = 17.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.5.dp,
                                color = Color(0xFF0A0C12)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Color(0xFF0A0C12),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = when {
                                !engineReady -> "Starting Engine..."
                                isDownloading -> "Downloading..."
                                else -> "Download to Gallery"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0A0C12)
                        )
                    }
                }

                AnimatedVisibility(visible = isDownloading) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF2B1822))
                            .border(1.dp, Color(0xFFFF4D6D).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .clickable {
                                try {
                                    YoutubeDL.getInstance().destroyProcessById(processId)
                                    isDownloading = false
                                    statusMessage = "Download cancelled"
                                } catch (_: Exception) { }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cancel",
                            tint = Color(0xFFFF4D6D)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QualityChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = 0.22f) else Color(0xFF0C0F18),
        animationSpec = tween(200),
        label = "chipBg"
    )
    val border by animateColorAsState(
        targetValue = if (selected) accentColor else Color.White.copy(alpha = 0.08f),
        animationSpec = tween(200),
        label = "chipBorder"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else Color(0xFF9AA3B8)
        )
    }
}

suspend fun downloadAndSaveToGallery(
    context: Context,
    url: String,
    mediaMode: MediaMode,
    videoQuality: VideoQuality,
    audioQuality: AudioQuality,
    processId: String,
    onProgress: (Float, String) -> Unit
): Result<String> = withContext(Dispatchers.IO) {
    val workDir = File(context.cacheDir, "dl_work")
    workDir.deleteRecursively()
    workDir.mkdirs()

    try {
        val request = YoutubeDLRequest(url)
        request.addOption("--no-playlist")
        request.addOption("--no-mtime")
        request.addOption("-o", "${workDir.absolutePath}/%(title).70s_%(id)s.%(ext)s")

        if (mediaMode == MediaMode.VIDEO) {
            request.addOption("-f", videoQuality.formatString)
            request.addOption("--merge-output-format", "mp4")
        } else {
            request.addOption("-x")
            request.addOption("--audio-format", audioQuality.format)
            request.addOption("--audio-quality", audioQuality.quality)
        }

        YoutubeDL.getInstance().execute(request, processId) { progress, _, line ->
            onProgress(progress, line)
        }

        val outputFile = workDir.listFiles()
            ?.filter {
                it.isFile &&
                    !it.name.endsWith(".part") &&
                    !it.name.endsWith(".ytdl") &&
                    !it.name.endsWith(".temp")
            }
            ?.maxByOrNull { it.lastModified() }
            ?: return@withContext Result.failure(Exception("No output file produced"))

        val savedName = saveFileToMediaStore(context, outputFile, mediaMode == MediaMode.VIDEO)
        workDir.deleteRecursively()
        Result.success(savedName)
    } catch (e: Exception) {
        workDir.deleteRecursively()
        Result.failure(e)
    }
}

fun saveFileToMediaStore(context: Context, file: File, isVideo: Boolean): String {
    val ext = file.extension.lowercase()
    val defaultMime = if (isVideo) "video/mp4" else "audio/mpeg"
    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: defaultMime

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val relativePath = if (isVideo) {
            "${Environment.DIRECTORY_MOVIES}/MediaSaver"
        } else {
            "${Environment.DIRECTORY_MUSIC}/MediaSaver"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: throw Exception("Failed to create MediaStore entry")

        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { input ->
                input.copyTo(out)
            }
        } ?: throw Exception("Failed to open output stream")

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    } else {
        val dirType = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC
        val targetDir = File(Environment.getExternalStoragePublicDirectory(dirType), "MediaSaver")
        if (!targetDir.exists()) targetDir.mkdirs()
        val targetFile = File(targetDir, file.name)
        file.copyTo(targetFile, overwrite = true)
        MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf(mimeType), null)
    }

    return file.name
}

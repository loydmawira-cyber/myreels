package com.example.ui

import android.content.Intent
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.AppColors
import com.example.data.SupabaseRepository
import com.example.data.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(videoId: String, repo: SupabaseRepository) {
    var video by remember { mutableStateOf<Video?>(null) }
    var videoUrl by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = ExoPlayer.REPEAT_MODE_ALL
            playWhenReady = true
        }
    }

    LaunchedEffect(videoId) {
        val v = repo.getVideo(videoId)
        video = v
        if (v?.video_path != null) {
            val u = repo.getSignedUrl(v.video_path)
            videoUrl = u
            if (u != null) {
                val mediaItem = MediaItem.fromUri(u)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    suspend fun downloadVideoToCache(): File? {
        val u = videoUrl ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val cacheDir = context.cacheDir
                val file = File(cacheDir, "$videoId.mp4")
                if (file.exists()) return@withContext file
                val inputStream = URL(u).openStream()
                val outputStream = FileOutputStream(file)
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                file
            } catch (e: Exception) {
                null
            }
        }
    }

    fun exportAndShareVideo() {
        busy = true
        scope.launch {
            val file = downloadVideoToCache()
            busy = false
            if (file != null) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Export & Share Reel"))
            } else {
                Toast.makeText(context, "Could not download video for export", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun saveToDevice() {
        busy = true
        scope.launch {
            val file = downloadVideoToCache()
            busy = false
            if (file != null) {
                Toast.makeText(context, "Reel saved to device cache ready for export", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Could not save video to device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val captionText = buildString {
        video?.script?.let {
            append(it.caption)
            append("\n\n")
            append(it.hashtags.joinToString(" ") { h -> "#$h" })
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(16.dp))
                .background(androidx.compose.ui.graphics.Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = video?.title ?: "Video",
            style = MaterialTheme.typography.titleLarge,
            fontSize = 20.sp
        )

        if (captionText.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Card)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Suggested caption", color = AppColors.Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    SelectionContainer {
                        Text(captionText, color = AppColors.Text, fontSize = 15.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { exportAndShareVideo() },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
        ) {
            if (busy) {
                CircularProgressIndicator(color = AppColors.Text, modifier = Modifier.size(24.dp))
            } else {
                Text("Export & Share Reel", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { saveToDevice() },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Text)
        ) {
            Text("Save to local device", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.AppColors
import com.example.Constants
import com.example.data.Series
import com.example.data.SupabaseRepository
import com.example.data.Video
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SeriesDetailScreen(
    seriesId: String,
    repo: SupabaseRepository,
    onNavigateVideo: (String) -> Unit
) {
    var series by remember { mutableStateOf<Series?>(null) }
    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var thumbs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    suspend fun load() {
        val s = repo.getSeries(seriesId)
        val vList = repo.getVideos(seriesId)
        series = s
        videos = vList
        val newThumbs = thumbs.toMutableMap()
        for (v in vList) {
            if (v.thumbnail_path != null && !newThumbs.containsKey(v.id)) {
                val u = repo.getSignedUrl(v.thumbnail_path)
                if (u != null) {
                    newThumbs[v.id] = u
                }
            }
        }
        thumbs = newThumbs
    }

    LaunchedEffect(seriesId) {
        load()
        while (true) {
            delay(4000)
            val vList = repo.getVideos(seriesId)
            videos = vList
            if (vList.any { it.status != "done" && it.status != "failed" }) {
                load()
            }
        }
    }

    fun generate() {
        busy = true
        scope.launch {
            val res = repo.requestVideo(seriesId)
            busy = false
            res.fold(
                onSuccess = { load() },
                onFailure = { Toast.makeText(context, it.localizedMessage ?: "Could not start video generation", Toast.LENGTH_LONG).show() }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = series?.name ?: "Series",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Button(
                onClick = { generate() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
            ) {
                Text("Generate a video", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("Videos", color = AppColors.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (videos.isEmpty()) {
                    item {
                        Text(
                            text = "Nothing yet. Tap “Generate a video”.",
                            color = AppColors.Muted,
                            modifier = Modifier.padding(top = 20.dp)
                        )
                    }
                }

                items(videos) { item ->
                    val isDone = item.status == "done"
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onNavigateVideo(item.id) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = AppColors.Card)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val thumbUrl = thumbs[item.id]
                            if (thumbUrl != null) {
                                AsyncImage(
                                    model = thumbUrl,
                                    contentDescription = "Thumbnail",
                                    modifier = Modifier
                                        .width(54.dp)
                                        .height(96.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .width(54.dp)
                                        .height(96.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AppColors.Border)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title ?: "New video",
                                    color = AppColors.Text,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val statusText = Constants.STATUS_LABELS[item.status] ?: item.status
                                val progressStr = if (item.status != "done" && item.status != "failed") " · ${item.progress}%" else ""
                                Text(
                                    text = "$statusText$progressStr",
                                    color = if (item.status == "failed") AppColors.Danger else AppColors.Muted,
                                    fontSize = 14.sp
                                )

                                if (item.status != "done" && item.status != "failed") {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { item.progress / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = AppColors.Accent,
                                        trackColor = AppColors.Border
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // CircularProgressIndicator overlay while AI generates script or renders video
        if (busy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Card)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = AppColors.Accent, modifier = Modifier.size(48.dp))
                        Text(
                            text = "AI is generating script & rendering video...",
                            color = AppColors.Text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

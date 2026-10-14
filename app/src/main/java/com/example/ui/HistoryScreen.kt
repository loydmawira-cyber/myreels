package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.AppColors
import com.example.Constants
import com.example.data.SupabaseRepository
import com.example.data.Video
import kotlinx.coroutines.launch

data class VideoHistoryItem(
    val video: Video,
    val seriesName: String,
    val thumbnailUrl: String?
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    repo: SupabaseRepository,
    onNavigateVideo: (String) -> Unit
) {
    var historyItems by remember { mutableStateOf<List<VideoHistoryItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var itemToDelete by remember { mutableStateOf<VideoHistoryItem?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    fun loadHistory() {
        scope.launch {
            loading = true
            val seriesList = repo.getSeriesList()
            val items = mutableListOf<VideoHistoryItem>()
            for (series in seriesList) {
                val videos = repo.getVideos(series.id)
                for (v in videos) {
                    val thumb = if (v.thumbnail_path != null) repo.getSignedUrl(v.thumbnail_path) else null
                    items.add(VideoHistoryItem(v, series.name, thumb))
                }
            }
            historyItems = items.sortedByDescending { it.video.created_at }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        loadHistory()
    }

    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Reel") },
            text = { Text("Are you sure you want to delete '${itemToDelete?.video?.title ?: "this video"}'? This frees up your storage.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = itemToDelete
                        itemToDelete = null
                        if (target != null) {
                            scope.launch {
                                val success = repo.deleteVideo(target.video.id)
                                if (success) {
                                    Toast.makeText(context, "Reel deleted successfully", Toast.LENGTH_SHORT).show()
                                    loadHistory()
                                } else {
                                    Toast.makeText(context, "Could not delete reel", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "Video Generation History",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Tap to view or long-press/tap delete to manage storage.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (historyItems.isEmpty() && !loading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No video history found. Generate some videos first!",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            items(historyItems) { item ->
                val isDone = item.video.status == "done"
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(
                            onClick = {
                                if (isDone) onNavigateVideo(item.video.id)
                            },
                            onLongClick = {
                                itemToDelete = item
                            }
                        ),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.thumbnailUrl != null) {
                            AsyncImage(
                                model = item.thumbnailUrl,
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
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.video.title ?: "Untitled Reel",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Series: ${item.seriesName}",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val statusText = Constants.STATUS_LABELS[item.video.status] ?: item.video.status
                            val progressStr = if (item.video.status != "done" && item.video.status != "failed") " · ${item.video.progress}%" else ""
                            Text(
                                text = "$statusText$progressStr",
                                color = if (item.video.status == "failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                        }

                        IconButton(onClick = { itemToDelete = item }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Video",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

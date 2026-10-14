package com.example.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.Constants
import com.example.data.Quota
import com.example.data.Series
import com.example.data.SupabaseRepository
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    repo: SupabaseRepository,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onNavigateNewSeries: () -> Unit,
    onNavigateSeries: (String) -> Unit,
    onNavigateSelectNiche: () -> Unit,
    onNavigateSelectVoice: () -> Unit,
    onNavigateHistory: () -> Unit,
    onNavigateSettings: () -> Unit,
    onSignedOut: () -> Unit
) {
    var seriesList by remember { mutableStateOf<List<Series>>(emptyList()) }
    var quota by remember { mutableStateOf<Quota?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadData() {
        loading = true
        scope.launch {
            val sList = repo.getSeriesList()
            val q = repo.getQuota()
            seriesList = sList
            quota = q
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    fun label(list: List<com.example.OptionItem>, id: String): String {
        return list.find { it.id == id }?.label ?: id
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Top Bar with Theme Toggle & Settings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MyReels",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isDarkTheme) "Dark" else "Light",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = onToggleTheme,
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quota Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (quota?.free_mode == true) "Free videos left today" else "Credits left",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 15.sp
                )
                Text(
                    text = if (quota == null) "–" else if (quota!!.free_mode) maxOf(0, quota!!.daily_limit - quota!!.used_today).toString() else quota!!.credits.toString(),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Series List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (seriesList.isEmpty() && !loading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No series yet. Create one to make your first video.",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            items(seriesList) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onNavigateSeries(item.id) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = item.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${label(Constants.NICHES, item.niche)} · ${label(Constants.ART_STYLES, item.art_style)} · ${item.target_seconds}s",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNavigateNewSeries,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("+ New series", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onNavigateSelectNiche,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Niche", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onNavigateSelectVoice,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Voice", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onNavigateHistory,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("History", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onNavigateSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Settings & Ambient Music", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = {
                repo.signOut()
                onSignedOut()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign out", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
    }
}

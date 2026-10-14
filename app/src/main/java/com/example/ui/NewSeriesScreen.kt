package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.Constants
import com.example.OptionItem
import com.example.data.SupabaseRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewSeriesScreen(
    repo: SupabaseRepository,
    onSeriesCreated: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var niche by remember { mutableStateOf("scary_stories") }
    var custom by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("cinematic") }
    var voice by remember { mutableStateOf("onyx") }
    var seconds by remember { mutableStateOf(45) }
    var music by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    @Composable
    fun ChipGroup(title: String, options: List<OptionItem>, selectedId: String, onSelect: (String) -> Unit) {
        Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { opt ->
                val selected = opt.id == selectedId
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(opt.id) },
                    label = { Text(opt.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        selectedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Create New Series & Reel Project", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))

        // Project Name Input Field
        Text("1. Reel Project Name", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Project title / series name") },
            placeholder = { Text("e.g. Midnight Scary Stories #1") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))
        ChipGroup("2. Niche", Constants.NICHES, niche) { niche = it }

        if (niche == "custom") {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it },
                label = { Text("Your custom topic") },
                placeholder = { Text("e.g. weird animal facts for kids") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        ChipGroup("3. Art style", Constants.ART_STYLES, style) { style = it }

        Spacer(modifier = Modifier.height(12.dp))
        ChipGroup("4. Voice", Constants.VOICES, voice) { voice = it }

        Spacer(modifier = Modifier.height(12.dp))
        Text("5. Desired Reel Length ($seconds seconds)", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
        
        Slider(
            value = seconds.toFloat(),
            onValueChange = { seconds = it.toInt() },
            valueRange = 15f..90f,
            steps = 4,
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Constants.DURATIONS.forEach { d ->
                TextButton(onClick = { seconds = d }) {
                    Text(
                        text = "${d}s",
                        color = if (seconds == d) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        fontWeight = if (seconds == d) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("6. Additional Details", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp))

        OutlinedTextField(
            value = music,
            onValueChange = { music = it },
            label = { Text("Background music link (optional, .mp3)") },
            placeholder = { Text("https://...") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (niche == "custom" && custom.isBlank()) {
                    Toast.makeText(context, "Please describe your custom topic", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                busy = true
                scope.launch {
                    val defaultName = Constants.NICHES.find { id -> id.id == niche }?.label ?: "Series"
                    val finalName = if (name.isBlank()) defaultName else name.trim()
                    val res = repo.createSeries(
                        name = finalName,
                        niche = niche,
                        customPrompt = if (niche == "custom") custom.trim() else null,
                        artStyle = style,
                        voice = voice,
                        targetSeconds = seconds,
                        musicUrl = music.ifBlank { null }
                    )
                    busy = false
                    res.fold(
                        onSuccess = { created -> onSeriesCreated(created.id) },
                        onFailure = { Toast.makeText(context, it.localizedMessage ?: "Could not create series", Toast.LENGTH_LONG).show() }
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (busy) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            } else {
                Text("Create project", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

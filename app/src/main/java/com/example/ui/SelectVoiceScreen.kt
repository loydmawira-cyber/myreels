package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.AppColors
import com.example.Constants

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectVoiceScreen(
    viewModel: VoiceViewModel = viewModel(),
    onVoiceSelected: (String, String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val speakingStyles = listOf(
        com.example.OptionItem("narrative", "Narrative & Expressive"),
        com.example.OptionItem("dramatic", "Dramatic & Cinematic"),
        com.example.OptionItem("upbeat", "Upbeat & Energetic"),
        com.example.OptionItem("calm", "Calm & Meditative")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "AI Voice & Narration Style",
            style = MaterialTheme.typography.titleLarge,
            color = AppColors.Text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Choose the voice profile and speaking cadence for your video narration.",
            style = MaterialTheme.typography.bodyLarge,
            color = AppColors.Muted,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Text(
            text = "1. AI Voice Profile",
            color = AppColors.Text,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Constants.VOICES.forEach { voice ->
                val selected = voice.id == state.selectedVoice
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.selectVoice(voice.id) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) AppColors.Card else AppColors.Card.copy(alpha = 0.6f)
                    ),
                    border = if (selected) BorderStroke(2.dp, AppColors.Accent) else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = voice.label,
                                color = AppColors.Text,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Profile ID: ${voice.id}",
                                color = AppColors.Muted,
                                fontSize = 13.sp
                            )
                        }
                        RadioButton(
                            selected = selected,
                            onClick = { viewModel.selectVoice(voice.id) },
                            colors = RadioButtonDefaults.colors(selectedColor = AppColors.Accent)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "2. Speaking Style & Cadence",
            color = AppColors.Text,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            speakingStyles.forEach { style ->
                val selected = style.id == state.speakingStyle
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.selectSpeakingStyle(style.id) },
                    label = { Text(style.label, fontSize = 15.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AppColors.Accent,
                        selectedLabelColor = AppColors.Text,
                        containerColor = AppColors.Card,
                        labelColor = AppColors.Muted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = AppColors.Border,
                        selectedBorderColor = AppColors.Accent
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = {
                Toast.makeText(context, "Voice profile & speaking style saved", Toast.LENGTH_SHORT).show()
                onVoiceSelected(state.selectedVoice, state.speakingStyle)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
        ) {
            Text("Save Voice Selection", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

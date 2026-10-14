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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.AppColors
import com.example.Constants

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectNicheScreen(
    viewModel: NicheViewModel = viewModel(),
    onNicheSelected: (String, String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "Select Content Niche",
            style = MaterialTheme.typography.titleLarge,
            color = AppColors.Text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Choose the topic category for your automated video series.",
            style = MaterialTheme.typography.bodyLarge,
            color = AppColors.Muted,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Constants.NICHES.forEach { nicheItem ->
                val selected = nicheItem.id == state.selectedNiche
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.selectNiche(nicheItem.id) },
                    label = { Text(nicheItem.label, fontSize = 15.sp) },
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

        if (state.selectedNiche == "custom") {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = state.customTopic,
                onValueChange = { viewModel.updateCustomTopic(it) },
                label = { Text("Custom Niche Topic") },
                placeholder = { Text("e.g. quantum physics explained simply") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.Accent,
                    unfocusedBorderColor = AppColors.Border,
                    focusedLabelColor = AppColors.Accent,
                    unfocusedLabelColor = AppColors.Muted,
                    focusedTextColor = AppColors.Text,
                    unfocusedTextColor = AppColors.Text
                )
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (state.selectedNiche == "custom" && state.customTopic.isBlank()) {
                    Toast.makeText(context, "Please enter your custom topic", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                onNicheSelected(state.selectedNiche, state.customTopic)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
        ) {
            Text("Save Niche Selection", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

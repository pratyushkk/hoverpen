package com.wordpilot.assistant.ui.model

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordpilot.assistant.domain.models.AIEngineType
import com.wordpilot.assistant.domain.models.ModelState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: ModelManagementViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Local AI Model") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Hardware Optimization Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Filled.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Column {
                        Text(
                            "Optimized for Motorola Edge 70 Pro",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            "8 GB RAM • Dimensity 7300 • English (Instruct)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Main Model Card with Actions
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isInstalled) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            if (uiState.isInstalled) Icons.Filled.CheckCircle else Icons.Filled.SmartToy,
                            contentDescription = null,
                            tint = if (uiState.isInstalled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                uiState.modelName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (uiState.isInstalled) "Installed & Ready On-Device" else "Meta Llama 3.2 (770 MB)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Progress or Status Content
                    when {
                        uiState.modelState == ModelState.DOWNLOADING -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LinearProgressIndicator(
                                    progress = { uiState.downloadProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${(uiState.downloadProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${uiState.downloadedMb} MB / ${uiState.totalMb} MB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedButton(
                                    onClick = { viewModel.cancelDownload() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cancel Download")
                                }
                            }
                        }

                        uiState.errorMessage != null -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        "Error: ${uiState.errorMessage}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                                Button(
                                    onClick = { viewModel.startDownload() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry Download")
                                }
                            }
                        }

                        uiState.isInstalled -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "On-device neural inference is ready. All text rephrasing, tone adjustments, and style suggestions process locally without internet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "Active Assistant Engine",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                uiState.selectedEngine.displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (uiState.selectedEngine == AIEngineType.LOCAL_AI) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        if (uiState.selectedEngine != AIEngineType.LOCAL_AI) {
                                            Button(
                                                onClick = { viewModel.setSelectedEngine(AIEngineType.LOCAL_AI) },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text("Use Local AI", style = MaterialTheme.typography.labelMedium)
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = { viewModel.setSelectedEngine(AIEngineType.GEMINI_FLASH) },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text("Switch to API", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = { viewModel.deleteModel() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Delete Model (${uiState.storageMb} MB)")
                                }
                            }
                        }

                        else -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "Download the quantized 4-bit Llama 3.2 model to enable offline AI text rewriting, tone change, and stylistic polishing on your device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { viewModel.startDownload() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Download Llama 3.2 1B (${uiState.totalMb} MB)")
                                }
                            }
                        }
                    }
                }
            }

            // Model Specification Details
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Technical Specifications",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    InfoRow("Model Architecture", "Meta Llama 3.2 Instruct")
                    InfoRow("Parameters", "1.23 Billion")
                    InfoRow("Quantization", "Q4_K_M (4-bit GGUF)")
                    InfoRow("Download Size", "770 MB")
                    InfoRow("RAM Footprint", "~850 MB (Optimal for 8GB device)")
                    InfoRow("Source", "bartowski / Hugging Face")
                    InfoRow("License", "Llama 3.2 Community License")
                    if (uiState.isInstalled) {
                        InfoRow("Storage Path", uiState.statusText)
                    }
                }
            }

            // Performance Benchmark (visible when installed)
            if (uiState.isInstalled) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Runtime Performance",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        InfoRow("Memory Usage", "${uiState.memoryMb} MB")
                        InfoRow("Load Time", "${uiState.loadTimeMs} ms")
                        InfoRow("Inference Speed", "${uiState.inferenceMs} ms / rewrite")
                    }
                }
            }

            // Privacy & Architecture Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "100% Private & Offline",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        "Hoverpen never transmits your keystrokes to third-party servers. " +
                        "Basic spelling and grammar rules execute instantly in milliseconds using deterministic trie tables. " +
                        "When Llama 3.2 is installed, intelligent tone adjustments and sentence rewrites run directly on your phone's processor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

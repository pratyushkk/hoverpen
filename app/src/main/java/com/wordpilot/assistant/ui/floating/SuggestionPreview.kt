package com.wordpilot.assistant.ui.floating

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordpilot.assistant.domain.models.AIEngineType
import com.wordpilot.assistant.ui.settings.AppearanceSettings
import com.wordpilot.assistant.ui.theme.AssistantColors

@Composable
fun SuggestionPreview(
    originalText: String,
    improvedText: String,
    isLoading: Boolean,
    errorMessage: String?,
    canUndo: Boolean,
    onReplace: () -> Unit,
    onInsert: () -> Unit,
    onCopy: () -> Unit,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    onSelectTone: (String) -> Unit,
    currentTone: String,
    settings: AppearanceSettings,
    selectedEngine: AIEngineType = AIEngineType.GEMINI_FLASH,
    onSelectEngine: (AIEngineType) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val textScrollState = rememberScrollState()
    val toneScrollState = rememberScrollState()

    val quickTones = remember {
        listOf("Professional", "Casual", "Concise", "Clarity", "Polite", "Polish")
    }

    GlassBackground(
        modifier = modifier
            .widthIn(min = 280.dp, max = 340.dp)
            .wrapContentHeight(),
        opacity = settings.glassOpacity.coerceAtLeast(0.85f),
        baseColor = Color(settings.glassTintArgb),
        cornerRadius = settings.glassCornerRadiusDp.dp,
        borderWidth = settings.glassBorderWidthDp.dp,
        borderOpacity = settings.glassBorderOpacity.coerceAtLeast(0.35f),
        highlightOpacity = settings.glassHighlightOpacity,
        shadowElevation = 18.dp,
        blurRadius = if (settings.expanderBlurEnabled) settings.expanderBlurRadiusDp.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = AssistantColors.AccentCyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Hoverpen",
                        color = AssistantColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text(selectedEngine.displayName, fontSize = 10.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                }

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cancel",
                        tint = AssistantColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Diff / Text display box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp, max = 130.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.28f))
                    .padding(8.dp)
                    .verticalScroll(textScrollState)
            ) {
                if (isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = AssistantColors.AccentCyan,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Rewriting with Gemini Model...",
                            color = AssistantColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                } else if (errorMessage != null && improvedText.isBlank()) {
                    Text(
                        text = errorMessage,
                        color = AssistantColors.BadgeSpelling,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                } else {
                    Column {
                        // Original text with strikethrough if changed
                        if (originalText.isNotBlank() && originalText != improvedText) {
                            Text(
                                text = originalText,
                                color = AssistantColors.DiffOriginalText.copy(alpha = 0.75f),
                                fontSize = 12.sp,
                                textDecoration = TextDecoration.LineThrough,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // Improved text
                        Text(
                            text = if (improvedText.isNotBlank()) improvedText else originalText,
                            color = AssistantColors.TextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 18.sp
                        )

                        if (errorMessage != null && improvedText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Note: $errorMessage",
                                color = Color(0xFFFBBF24),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Tone Switcher Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(toneScrollState),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                quickTones.forEach { tone ->
                    val isSelected = currentTone.equals(tone, ignoreCase = true)
                    Surface(
                        onClick = { onSelectTone(tone) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF6366F1).copy(alpha = 0.35f) else Color(0xFF1E232E).copy(alpha = 0.5f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) Color(0xFF818CF8) else Color.White.copy(alpha = 0.08f)
                        )
                    ) {
                        Text(
                            text = tone,
                            color = if (isSelected) Color(0xFFA5B4FC) else AssistantColors.TextSecondary,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom action buttons: Replace, Insert, Copy, Undo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primary Apply / Replace Button
                Button(
                    onClick = onReplace,
                    enabled = !isLoading && improvedText.isNotBlank() && improvedText != originalText,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AssistantColors.AccentGreen,
                        contentColor = Color.Black,
                        disabledContainerColor = AssistantColors.AccentGreen.copy(alpha = 0.25f),
                        disabledContentColor = Color.White.copy(alpha = 0.4f)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Replace",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Replace text",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Copy Button
                OutlinedButton(
                    onClick = onCopy,
                    enabled = !isLoading && (improvedText.isNotBlank() || originalText.isNotBlank()),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AssistantColors.TextPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Insert Button
                OutlinedButton(
                    onClick = onInsert,
                    enabled = !isLoading && improvedText.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AssistantColors.TextPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Input,
                        contentDescription = "Insert",
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Undo Button (shown when available)
                if (canUndo) {
                    OutlinedButton(
                        onClick = onUndo,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AssistantColors.TextSecondary
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Undo,
                            contentDescription = "Undo",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

package com.wordpilot.assistant.ui.floating

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordpilot.assistant.WordPilotApp
import com.wordpilot.assistant.engine.impl.GeminiAIEngine
import com.wordpilot.assistant.ui.settings.AppearanceSettings
import com.wordpilot.assistant.ui.theme.AssistantColors

@Composable
fun FloatingGeminiKeySetupPanel(
    settings: AppearanceSettings,
    onKeySaved: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var apiKeyText by remember {
        mutableStateOf(WordPilotApp.instance.cachedGeminiApiKey ?: "")
    }
    var isPasswordVisible by remember { mutableStateOf(false) }

    GlassBackground(
        modifier = modifier
            .widthIn(max = 340.dp)
            .wrapContentHeight(),
        opacity = settings.glassOpacity.coerceAtLeast(0.85f),
        baseColor = Color(settings.glassTintArgb),
        cornerRadius = settings.glassCornerRadiusDp.dp,
        borderWidth = settings.glassBorderWidthDp.dp,
        borderOpacity = settings.glassBorderOpacity.coerceAtLeast(0.35f),
        highlightOpacity = settings.glassHighlightOpacity,
        shadowElevation = 18.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF8B5CF6), Color(0xFF06B6D4))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = "Gemini Model Setup",
                        color = AssistantColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = AssistantColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Explanation Card
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF6366F1).copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Activate Gemini AI",
                        color = Color(0xFFA5B4FC),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "The Gemini Model powers AI Rewriting and Tone Adjustments directly in this floating window.",
                        color = AssistantColors.TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // API Key Input Field
            Text(
                text = "Gemini API Key",
                color = AssistantColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = apiKeyText,
                onValueChange = { apiKeyText = it },
                placeholder = {
                    Text(
                        "AIzaSy...",
                        color = AssistantColors.TextMuted,
                        fontSize = 12.sp
                    )
                },
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        val sanitized = GeminiAIEngine.sanitizeApiKey(apiKeyText)
                        if (sanitized.isNotBlank()) {
                            onKeySaved(sanitized)
                        }
                    }
                ),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { isPasswordVisible = !isPasswordVisible },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "Toggle Visibility",
                                tint = AssistantColors.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (apiKeyText.isNotEmpty()) {
                            IconButton(
                                onClick = { apiKeyText = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Clear",
                                    tint = AssistantColors.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = AssistantColors.AccentCyan,
                    unfocusedBorderColor = AssistantColors.Divider,
                    focusedContainerColor = Color(0xFF1E232E).copy(alpha = 0.5f),
                    unfocusedContainerColor = Color(0xFF1E232E).copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Helper Buttons: Paste & Get Key
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Paste from clipboard
                Surface(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = clipboard?.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val pasted = clip.getItemAt(0).text?.toString() ?: ""
                            val sanitized = GeminiAIEngine.sanitizeApiKey(pasted)
                            if (sanitized.isNotBlank()) {
                                apiKeyText = sanitized
                                Toast.makeText(context, "Pasted and cleaned API key", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E232E).copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, AssistantColors.Divider)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentPaste,
                            contentDescription = null,
                            tint = AssistantColors.AccentCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Paste",
                            color = AssistantColors.AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Get Free Key Link
                Row(
                    modifier = Modifier.clickable {
                        try {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey")).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(browserIntent)
                        } catch (e: android.content.ActivityNotFoundException) {
                            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                        } catch (e: SecurityException) {
                            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                        }
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Get Free API Key",
                        color = Color(0xFF60A5FA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = null,
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onClose,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AssistantColors.TextSecondary),
                    border = BorderStroke(1.dp, AssistantColors.Divider),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        val sanitized = GeminiAIEngine.sanitizeApiKey(apiKeyText)
                        if (sanitized.isBlank()) {
                            Toast.makeText(context, "Please enter or paste your Gemini API key", Toast.LENGTH_SHORT).show()
                        } else {
                            onKeySaved(sanitized)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AssistantColors.AccentGreen,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Save Key",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

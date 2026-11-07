package com.wordpilot.assistant.ui.settings

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordpilot.assistant.ui.floating.ALL_TONE_DEFINITIONS
import com.wordpilot.assistant.ui.floating.GlassBackground
import com.wordpilot.assistant.ui.theme.AssistantColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiWindowCustomizationScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(AiWindowSettings.load(context)) }
    var newPromptText by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    fun updateSettings(newSettings: AiWindowSettings) {
        settings = newSettings
        AiWindowSettings.save(context, newSettings)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "AI Window Studio",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Customize Floating AI Chat & Panels",
                            fontSize = 11.sp,
                            color = AssistantColors.TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val defaultSettings = AiWindowSettings()
                        updateSettings(defaultSettings)
                    }) {
                        Text("Reset", color = Color(0xFF10B981), fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F1117)
                )
            )
        },
        containerColor = Color(0xFF0A0C10)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Live Interactive Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF131720))
                    .border(1.dp, Color(0xFF262C38), RoundedCornerShape(20.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Miniature Mockup of the Floating AI Window
                GlassBackground(
                    modifier = Modifier
                        .width((settings.windowWidthDp * 0.72f).dp)
                        .height((settings.windowHeightDp * 0.38f).dp),
                    opacity = settings.backgroundOpacity,
                    baseColor = Color(settings.backgroundTintArgb),
                    cornerRadius = (settings.cornerRadiusDp * 0.7f).dp,
                    borderWidth = settings.borderWidthDp.dp,
                    borderOpacity = 0.5f,
                    highlightOpacity = 0.25f,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Hoverpen AI", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(10.dp)
                            )
                        }

                        // Mock Chat bubble inside preview
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1F2430))
                                .padding(6.dp)
                        ) {
                            Text(
                                text = "AI streaming at ${settings.streamingSpeed.displayName}…",
                                color = Color(0xFFE5E7EB),
                                fontSize = 8.sp
                            )
                        }

                        // Mock input field inside preview
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF11141A))
                                .border(0.6.dp, Color(0xFF374151), RoundedCornerShape(10.dp))
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text("Type prompt…", color = Color.Gray, fontSize = 8.sp)
                        }
                    }
                }
            }

            // Section 1: Dimensions
            StudioCard(title = "WINDOW DIMENSIONS") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SliderRow(
                        label = "Width",
                        value = settings.windowWidthDp,
                        range = 280f..380f,
                        unit = "dp",
                        onValueChange = { updateSettings(settings.copy(windowWidthDp = it)) }
                    )
                    SliderRow(
                        label = "Height",
                        value = settings.windowHeightDp,
                        range = 360f..560f,
                        unit = "dp",
                        onValueChange = { updateSettings(settings.copy(windowHeightDp = it)) }
                    )
                    SliderRow(
                        label = "Corner Radius",
                        value = settings.cornerRadiusDp,
                        range = 12f..36f,
                        unit = "dp",
                        onValueChange = { updateSettings(settings.copy(cornerRadiusDp = it)) }
                    )
                }
            }

            // Section 2: Glass Theme Presets
            StudioCard(title = "GLASS & STYLING") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WindowGlassPreset.values().forEach { preset ->
                        val isSelected = settings.glassPreset == preset
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFF1E2638) else Color(0xFF141720))
                                .border(1.dp, if (isSelected) Color(0xFF10B981) else Color(0xFF262C38), RoundedCornerShape(12.dp))
                                .clickable {
                                    updateSettings(
                                        settings.copy(
                                            glassPreset = preset,
                                            backgroundOpacity = preset.opacity
                                        )
                                    )
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = preset.displayName,
                                color = if (isSelected) Color.White else AssistantColors.TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Section 3: Screen Docking Position
            StudioCard(title = "WINDOW ANCHOR POSITION") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WindowAnchorPosition.values().forEach { anchor ->
                        val isSelected = settings.anchorPosition == anchor
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFF1E2638) else Color(0xFF141720))
                                .border(1.dp, if (isSelected) Color(0xFF10B981) else Color(0xFF262C38), RoundedCornerShape(12.dp))
                                .clickable {
                                    updateSettings(settings.copy(anchorPosition = anchor))
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = anchor.displayName,
                                color = if (isSelected) Color.White else AssistantColors.TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Section 4: Typewriter Streaming Speed
            StudioCard(title = "AI TYPEWRITER PACING") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StreamingSpeed.values().forEach { speed ->
                        val isSelected = settings.streamingSpeed == speed
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFF10B981) else Color(0xFF171B24))
                                .border(1.dp, if (isSelected) Color(0xFF34D399) else Color(0xFF2E3544), RoundedCornerShape(12.dp))
                                .clickable {
                                    updateSettings(settings.copy(streamingSpeed = speed))
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = speed.displayName.split(" ")[0],
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Section 5: Floating Quick Tone Selection
            StudioCard(title = "FLOATING QUICK TONE OPTIONS") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Hoverpen defaults to 4 core quick tones (Fix & Polish, Short, Rewrite, Polite). Choose which tone options appear in your floating panel:",
                        color = AssistantColors.TextSecondary,
                        fontSize = 12.sp
                    )

                    ALL_TONE_DEFINITIONS.forEach { toneDef ->
                        val isEnabled = toneDef.id in settings.enabledToneStyles
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isEnabled) Color(0xFF1E2638) else Color(0xFF141720))
                                .border(
                                    1.dp,
                                    if (isEnabled) toneDef.accentColor.copy(alpha = 0.6f) else Color(0xFF262C38),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    val current = settings.enabledToneStyles.toMutableList()
                                    if (isEnabled) {
                                        if (current.size > 1) {
                                            current.remove(toneDef.id)
                                        }
                                    } else {
                                        current.add(toneDef.id)
                                    }
                                    updateSettings(settings.copy(enabledToneStyles = current))
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(toneDef.accentColor.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = toneDef.icon,
                                        contentDescription = null,
                                        tint = toneDef.accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = toneDef.title,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = toneDef.subtitle,
                                        color = AssistantColors.TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { checked ->
                                    val current = settings.enabledToneStyles.toMutableList()
                                    if (!checked) {
                                        if (current.size > 1) {
                                            current.remove(toneDef.id)
                                        }
                                    } else {
                                        if (!current.contains(toneDef.id)) {
                                            current.add(toneDef.id)
                                        }
                                    }
                                    updateSettings(settings.copy(enabledToneStyles = current))
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = toneDef.accentColor,
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color(0xFF1E232E)
                                )
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            updateSettings(settings.copy(enabledToneStyles = AiWindowSettings.DEFAULT_TONE_STYLES))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981))
                    ) {
                        Text("Reset to 4 Default Tones (Fix & Polish, Short, Rewrite, Polite)", fontSize = 11.sp)
                    }
                }
            }

            // Section 6: Custom Prompts Manager
            StudioCard(title = "CUSTOM PROMPT SHORTCUTS") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Prompt chips list
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        settings.customPrompts.forEach { prompt ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF141720))
                                    .border(0.8.dp, Color(0xFF282F3D), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = prompt,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Delete",
                                    tint = Color.Gray,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            val updated = settings.customPrompts.filter { it != prompt }
                                            updateSettings(settings.copy(customPrompts = updated))
                                        }
                                )
                            }
                        }
                    }

                    // Add new prompt row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = newPromptText,
                            onValueChange = { newPromptText = it },
                            placeholder = { Text("Add custom prompt…", fontSize = 12.sp, color = Color.Gray) },
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF141720))
                                .border(0.8.dp, Color(0xFF282F3D), RoundedCornerShape(12.dp)),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = Color(0xFF10B981),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (newPromptText.isNotBlank()) {
                                    val updated = settings.customPrompts + newPromptText.trim()
                                    newPromptText = ""
                                    updateSettings(settings.copy(customPrompts = updated))
                                }
                            },
                            enabled = newPromptText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Add", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun StudioCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF10131A))
            .border(1.dp, Color(0xFF1F2430), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = Color(0xFF10B981),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = AssistantColors.TextSecondary, fontSize = 12.sp)
            Text("${value.toInt()} $unit", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF10B981),
                activeTrackColor = Color(0xFF10B981),
                inactiveTrackColor = Color(0xFF262C38)
            )
        )
    }
}

package com.wordpilot.assistant.ui.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wordpilot.assistant.ui.theme.AssistantColors
import com.wordpilot.assistant.ui.theme.AssistantDimensions

@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    baseColor: Color = AssistantColors.SmokedGlassBase,
    opacity: Float = AssistantColors.DefaultGlassOpacity,
    cornerRadius: Dp = AssistantDimensions.DefaultCornerRadius,
    borderWidth: Dp = AssistantDimensions.DefaultBorderWidth,
    borderColor: Color = AssistantColors.GlassBorder,
    borderOpacity: Float = AssistantColors.DefaultBorderOpacity,
    highlightOpacity: Float = AssistantColors.DefaultHighlightOpacity,
    shadowElevation: Dp = AssistantDimensions.DefaultElevation,
    blurRadius: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val effectiveBackgroundColor = baseColor.copy(alpha = opacity.coerceIn(0f, 1f))
    val effectiveBorderColor = if (blurRadius > 0.dp) {
        Color.White.copy(alpha = borderOpacity.coerceAtLeast(0.18f))
    } else {
        borderColor.copy(alpha = borderOpacity.coerceIn(0f, 1f))
    }

    val shadowModifier = if (shadowElevation > 0.dp) {
        Modifier.shadow(
            elevation = shadowElevation,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.5f),
            spotColor = Color.Black.copy(alpha = 0.6f)
        )
    } else {
        Modifier
    }

    val borderModifier = if (borderWidth > 0.dp && effectiveBorderColor.alpha > 0f) {
        Modifier.border(borderWidth, effectiveBorderColor, shape = shape)
    } else {
        Modifier
    }

    val bgModifier = if (effectiveBackgroundColor.alpha > 0f) {
        if (blurRadius > 0.dp) {
            Modifier.background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF232938).copy(alpha = opacity.coerceIn(0.6f, 0.95f)),
                        baseColor.copy(alpha = opacity.coerceIn(0.7f, 0.98f))
                    )
                ),
                shape = shape
            )
        } else {
            Modifier.background(effectiveBackgroundColor, shape = shape)
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(shadowModifier)
            .then(bgModifier)
            .then(borderModifier)
            .clip(shape)
    ) {
        // Subtle specular reflective highlight along the top edge
        if (highlightOpacity > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.TopCenter)
                    .clip(shape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = highlightOpacity.coerceIn(0f, 1f)),
                                Color.White.copy(alpha = (highlightOpacity * 0.8f).coerceIn(0f, 1f)),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        content()
    }
}

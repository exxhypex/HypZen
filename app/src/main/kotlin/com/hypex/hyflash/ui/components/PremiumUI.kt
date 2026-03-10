package com.hypex.hyflash.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypex.hyflash.ui.theme.Mint

@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.2f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(20.dp)
    ) {
        Column { content() }
    }
}

@Composable
fun PremiumProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "ProgressBarAnimation"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Mint,
                            Mint.copy(alpha = 0.7f),
                            Mint.copy(alpha = 0.9f)
                        )
                    )
                )
                .drawBehind {
                    // Subtle glow effect
                    drawCircle(
                        color = Mint.copy(alpha = 0.3f),
                        radius = size.height * 2,
                        center = Offset(size.width, size.height / 2)
                    )
                }
        )
    }
}

@Composable
fun PremiumLoadingSpinner(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "Spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer { rotationZ = rotation },
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { 0.7f },
            modifier = Modifier.fillMaxSize(),
            color = Mint,
            strokeWidth = 4.dp,
            strokeCap = StrokeCap.Round,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        )
    }
}

@Composable
fun AnimatedBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "Background")
    
    val animX1 by infiniteTransition.animateFloat(
        initialValue = -100f, targetValue = 400f,
        animationSpec = infiniteRepeatable(tween(15000, easing = LinearEasing), RepeatMode.Reverse),
        label = "X1"
    )
    val animY1 by infiniteTransition.animateFloat(
        initialValue = -100f, targetValue = 800f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Reverse),
        label = "Y1"
    )

    val animX2 by infiniteTransition.animateFloat(
        initialValue = 400f, targetValue = -200f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse),
        label = "X2"
    )
    val animY2 by infiniteTransition.animateFloat(
        initialValue = 800f, targetValue = -100f,
        animationSpec = infiniteRepeatable(tween(22000, easing = LinearEasing), RepeatMode.Reverse),
        label = "Y2"
    )

    Box(modifier = modifier.fillMaxSize().blur(100.dp)) {
        // Blob 1
        Box(
            modifier = Modifier
                .offset(x = animX1.dp, y = animY1.dp)
                .size(300.dp)
                .scale(1.5f)
                .background(Mint.copy(alpha = 0.15f), CircleShape)
        )
        // Blob 2
        Box(
            modifier = Modifier
                .offset(x = animX2.dp, y = animY2.dp)
                .size(250.dp)
                .scale(1.2f)
                .background(Color(0xFF8B5CF6).copy(alpha = 0.1f), CircleShape) // Purple accent
        )
    }
}

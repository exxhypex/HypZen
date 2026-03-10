package com.hypex.hypzen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hypex.hypzen.ui.theme.*

@Composable
fun ProgressBlocks(progress: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val totalBlocks = 20
        val filledBlocks = (progress * totalBlocks).toInt()

        repeat(totalBlocks) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < filledBlocks) Mint else MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}

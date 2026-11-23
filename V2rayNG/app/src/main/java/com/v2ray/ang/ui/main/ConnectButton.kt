package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.colorFabActive

/**
 * Large central connect/disconnect button with a double contour.
 */
@Composable
fun ConnectButton(
    isRunning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundBrush = if (isRunning) {
        Brush.radialGradient(
            colors = listOf(Color(0xFFE8FF8C), Color(0xFF4E7A12)),
            center = Offset(0.5f, 0.5f),
        )
    } else {
        Brush.radialGradient(
            colors = listOf(Color(0xFF7A7985), Color(0xFF050508)),
            center = Offset(0.5f, 0.5f),
        )
    }
    val description = stringResource(if (isRunning) R.string.acc_stop else R.string.acc_start)

    Box(
        modifier = modifier.size(196.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Outer contour (ring) with its own shadow
        Box(
            Modifier
                .size(196.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.45f),
                    spotColor = Color.Black.copy(alpha = 0.45f),
                )
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )

        // Inner button with its own shadow / glow
        Box(
            Modifier
                .size(172.dp)
                .shadow(
                    elevation = if (isRunning) 22.dp else 16.dp,
                    shape = CircleShape,
                    ambientColor = if (isRunning) {
                        colorFabActive.copy(alpha = 0.40f)
                    } else {
                        Color.Black.copy(alpha = 0.60f)
                    },
                    spotColor = if (isRunning) {
                        colorFabActive.copy(alpha = 0.40f)
                    } else {
                        Color.Black.copy(alpha = 0.60f)
                    },
                )
                .clip(CircleShape)
                .background(backgroundBrush)
                .then(
                    if (isRunning) {
                        Modifier
                    } else {
                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    }
                )
                .semantics { contentDescription = description }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_power_24dp),
                contentDescription = null,
                tint = if (isRunning) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
            )
        }
    }
}

package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import kotlinx.coroutines.delay

@Composable
fun ConnectButton(
    isRunning: Boolean,
    startTime: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val innerBrush = if (isRunning) {
        Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFFB2CC77),
                0.25f to Color(0xFFA6C068),
                0.50f to Color(0xFF92AC4F),
                0.70f to Color(0xFF728F31),
                0.85f to Color(0xFF54731B),
                0.95f to Color(0xFF3C590B),
                1.00f to Color(0xFF2F4806)
            )
        )
    } else {
        Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFF75737F),
                0.25f to Color(0xFF6A6874),
                0.50f to Color(0xFF5A5864),
                0.70f to Color(0xFF44434D),
                0.85f to Color(0xFF2D2C35),
                0.95f to Color(0xFF18171E),
                1.00f to Color(0xFF06060B)
            )
        )
    }
    val glow = if (isRunning) Color(0xFFC6FF3D).copy(alpha = 0.40f)
    else Color.Black.copy(alpha = 0.60f)
    val textColor = if (isRunning) Color(0xFF1C2A00) else Color(0xFFE2E0EA)
    val ringBase = MaterialTheme.colorScheme.surfaceContainerHighest
    val ringDark = lerp(ringBase, Color.Black, 0.45f)
    val ringMid = lerp(ringBase, Color.Black, 0.15f)
    val ringLight = lerp(ringBase, Color.White, 0.10f)

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startTime, isRunning) {
        now = System.currentTimeMillis()
        while (isRunning) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val elapsedSeconds = if (isRunning && startTime != null) {
        ((now - startTime) / 1000).coerceAtLeast(0)
    } else 0
    val elapsedText = run {
        val hours = elapsedSeconds / 3600
        val minutes = (elapsedSeconds % 3600) / 60
        val seconds = elapsedSeconds % 60
        if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
        else String.format("%02d:%02d", minutes, seconds)
    }
    val description = stringResource(if (isRunning) R.string.acc_stop else R.string.acc_start)

    Box(modifier = modifier.size(196.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(196.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.45f),
                    spotColor = Color.Black.copy(alpha = 0.45f)
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to ringDark,
                            0.70f to ringDark,
                            0.88f to ringMid,
                            1.00f to ringLight
                        )
                    )
                )
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
        )
        Box(
            Modifier
                .size(172.dp)
                .shadow(
                    elevation = if (isRunning) 22.dp else 16.dp,
                    shape = CircleShape,
                    ambientColor = glow,
                    spotColor = glow
                )
                .clip(CircleShape)
                .background(innerBrush)
                .then(
                    if (isRunning) {
                        Modifier
                    } else {
                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    }
                )
                .semantics { contentDescription = description }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (isRunning) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DeepPowerIcon(tint = textColor, sizeDp = 44)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.connect_button_connected),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor
                    )
                    if (startTime != null) {
                        Text(
                            text = elapsedText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = textColor.copy(alpha = 0.85f)
                        )
                    }
                }
            } else {
                DeepPowerIcon(tint = textColor, sizeDp = 72)
            }
        }
    }
}

@Composable
private fun DeepPowerIcon(tint: Color, sizeDp: Int) {
    Box(contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(R.drawable.ic_power_24dp),
            contentDescription = null,
            tint = Color(0x73FFFFFF),
            modifier = Modifier.size(sizeDp.dp).offset(x = 1.5.dp, y = 2.dp)
        )
        Icon(
            painter = painterResource(R.drawable.ic_power_24dp),
            contentDescription = null,
            tint = Color(0x8C000000),
            modifier = Modifier.size(sizeDp.dp).offset(x = (-1).dp, y = (-1.5).dp)
        )
        Icon(
            painter = painterResource(R.drawable.ic_power_24dp),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(sizeDp.dp)
        )
    }
}

package com.ggmacro.app.ui.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ggmacro.app.service.FloatingOverlayService.OverlayState
import com.ggmacro.app.ui.theme.*
import kotlinx.coroutines.flow.StateFlow

@Composable
fun FloatingOverlayContent(
    macroName: String,
    state: StateFlow<OverlayState>,
    onRecord: () -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit,
    onHoldStart: () -> Unit = {},
    onHoldEnd: () -> Unit = {}
) {
    val currentState by state.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    val isRecording = currentState == OverlayState.RECORDING
    val isPlaying   = currentState == OverlayState.PLAYING

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.12f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "scale"
    )

    val mainColor by animateColorAsState(
        targetValue = when {
            isRecording -> NeonRed
            isPlaying   -> NeonCyan
            else        -> NeonPurple
        },
        label = "color"
    )

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(8.dp)
    ) {
        // ── Expanded control panel ────────────────────────────────────────
        if (expanded) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(GamingCard.copy(alpha = 0.95f))
                    .border(1.dp, mainColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = macroName.ifBlank { "GG Macro" },
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                HorizontalDivider(color = GamingBorder, thickness = 0.5.dp)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverlayIconButton(
                        icon  = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                        tint  = NeonRed,
                        label = if (isRecording) "Stop Rec" else "Record",
                        onClick = onRecord,
                        scale = if (isRecording) pulseScale else 1f
                    )
                    OverlayIconButton(
                        icon  = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        tint  = NeonCyan,
                        label = if (isPlaying) "Stop" else "Play",
                        onClick = if (isPlaying) onStop else onPlay,
                        scale = if (isPlaying) pulseScale else 1f
                    )
                }

                TextButton(
                    onClick = onClose,
                    colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Close Overlay", fontSize = 10.sp)
                }
            }
        }

        // ── Main floating button ─────────────────────────────────────────
        // Short tap  → toggle expanded panel
        // Press & hold → start macro immediately; release → stop macro
        Box(
            modifier = Modifier
                .size(56.dp)
                .scale(if ((isRecording || isPlaying) && !expanded) pulseScale else 1f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(mainColor.copy(alpha = 0.3f), GamingCard)
                    )
                )
                .border(2.dp, mainColor, CircleShape)
                // ✅ Hold-to-start: press = start macro instantly, release = stop macro
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { _ ->
                            android.util.Log.d("GGMacro", "FloatingOverlay ACTION_DOWN — starting macro")
                            onHoldStart()
                            tryAwaitRelease()
                            android.util.Log.d("GGMacro", "FloatingOverlay ACTION_UP — stopping macro")
                            onHoldEnd()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Tap indicator — tap to open/close the control panel
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = when {
                        isRecording -> Icons.Default.FiberManualRecord
                        isPlaying   -> Icons.Default.PlayArrow
                        else        -> Icons.Default.Games
                    },
                    contentDescription = "Toggle overlay",
                    tint = mainColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun OverlayIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    label: String,
    onClick: () -> Unit,
    scale: Float = 1f
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.scale(scale)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f))
                .border(1.dp, tint.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Text(label, fontSize = 9.sp, color = TextSecondary)
    }
}

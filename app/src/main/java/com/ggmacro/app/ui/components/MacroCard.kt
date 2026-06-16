package com.ggmacro.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ggmacro.app.data.model.Macro
import com.ggmacro.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MacroCard(
    macro: Macro,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onOpenOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = if (isPlaying) NeonCyan else GamingBorder,
        label = "border"
    )

    val dateStr = remember(macro.updatedAt) {
        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(macro.updatedAt))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(GamingCard, GamingCardElevated)
                )
            )
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
    ) {
        if (isPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(listOf(NeonCyan, NeonPurple))
                    )
            )
        }

        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(listOf(NeonCyan.copy(alpha = 0.2f), NeonPurple.copy(alpha = 0.2f)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isPlaying) NeonCyan else NeonPurple,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = macro.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = TextSecondary
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(GamingCardElevated)
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Edit, null, tint = NeonCyan) },
                        onClick = { showMenu = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = NeonPurple) },
                        onClick = { showMenu = false; onDuplicate() }
                    )
                    DropdownMenuItem(
                        text = { Text("Overlay", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.OpenInNew, null, tint = NeonGreen) },
                        onClick = { showMenu = false; onOpenOverlay() }
                    )
                    HorizontalDivider(color = GamingBorder)
                    DropdownMenuItem(
                        text = { Text("Delete", color = NeonRed) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = NeonRed) },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MacroChip(
                    label = if (macro.isInfiniteLoop) "∞ Loop" else "${macro.loopCount}x Loop",
                    color = NeonPurple
                )
                MacroChip(
                    label = "${macro.playbackSpeed}x Speed",
                    color = NeonCyan
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isPlaying) {
                    OutlinedButton(
                        onClick = onStop,
                        modifier = Modifier.weight(1f).height(40.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Stop", fontSize = 13.sp)
                    }
                } else {
                    Button(
                        onClick = onPlay,
                        modifier = Modifier.weight(1f).height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan.copy(alpha = 0.15f),
                            contentColor = NeonCyan
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GamingBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun MacroChip(label: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

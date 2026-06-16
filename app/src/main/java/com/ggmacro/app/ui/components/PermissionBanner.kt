package com.ggmacro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ggmacro.app.ui.theme.*

@Composable
fun PermissionBanner(
    hasOverlay: Boolean,
    hasAccessibility: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (hasOverlay && hasAccessibility) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NeonOrange.copy(alpha = 0.1f))
            .border(1.dp, NeonOrange.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = NeonOrange,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Permissions Required",
                style = MaterialTheme.typography.titleMedium,
                color = NeonOrange
            )
        }

        if (!hasAccessibility) {
            PermissionRow(
                label = "Accessibility Service — required for gesture playback",
                onRequest = onRequestAccessibility
            )
        }
        if (!hasOverlay) {
            PermissionRow(
                label = "Overlay Permission — required for floating button",
                onRequest = onRequestOverlay
            )
        }
    }
}

@Composable
private fun PermissionRow(label: String, onRequest: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
            fontSize = 12.sp
        )
        Spacer(Modifier.width(8.dp))
        TextButton(
            onClick = onRequest,
            colors = ButtonDefaults.textButtonColors(contentColor = NeonOrange)
        ) {
            Icon(Icons.Default.Settings, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Enable", fontSize = 12.sp)
        }
    }
}

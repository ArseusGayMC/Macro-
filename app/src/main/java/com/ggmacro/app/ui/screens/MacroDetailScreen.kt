package com.ggmacro.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ggmacro.app.data.model.ActionType
import com.ggmacro.app.data.model.MacroAction
import com.ggmacro.app.ui.theme.*
import com.ggmacro.app.viewmodel.MacroDetailViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: MacroDetailViewModel = hiltViewModel()
) {
    val macro by viewModel.macro.collectAsStateWithLifecycle()
    val actions by viewModel.actions.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val macroName by viewModel.macroName.collectAsStateWithLifecycle()
    val loopCount by viewModel.loopCount.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val tapDuration by viewModel.tapDuration.collectAsStateWithLifecycle()
    val actionDelay by viewModel.actionDelay.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = GamingBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (macro == null) "New Macro" else "Edit Macro",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                actions = {
                    if (actions.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearActions() }) {
                            Icon(Icons.Default.DeleteSweep, "Clear", tint = NeonRed)
                        }
                    }
                    TextButton(
                        onClick = { viewModel.saveMacro() },
                        colors = ButtonDefaults.textButtonColors(contentColor = NeonCyan)
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GamingDarkSurface)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp, bottom = 32.dp)
        ) {
            item {
                SectionCard(title = "Macro Name") {
                    OutlinedTextField(
                        value = macroName,
                        onValueChange = viewModel::setName,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter macro name", color = TextDisabled) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = GamingBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = NeonCyan
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            item {
                SectionCard(title = "Playback Settings") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SpeedSelector(
                            current = playbackSpeed,
                            onChange = viewModel::setPlaybackSpeed
                        )
                        LoopSelector(
                            current = loopCount,
                            onChange = viewModel::setLoopCount
                        )
                        NumberInput(
                            label = "Tap Duration (ms)",
                            value = tapDuration,
                            onChange = { viewModel.setTapDuration(it) }
                        )
                        NumberInput(
                            label = "Delay Between Actions (ms)",
                            value = actionDelay,
                            onChange = { viewModel.setActionDelay(it) }
                        )
                    }
                }
            }

            item {
                SectionCard(title = "Actions (${actions.size})") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showAddDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonPurple.copy(alpha = 0.2f),
                                    contentColor = NeonPurple
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Action", fontSize = 13.sp)
                            }

                            if (isPlaying) {
                                Button(
                                    onClick = { viewModel.stopPlayback() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonRed.copy(alpha = 0.2f),
                                        contentColor = NeonRed
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Stop", fontSize = 13.sp)
                                }
                            } else if (actions.isNotEmpty()) {
                                Button(
                                    onClick = { viewModel.playMacro() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonCyan.copy(alpha = 0.2f),
                                        contentColor = NeonCyan
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Test", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (actions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No actions yet. Use the floating overlay to record, or add manually.",
                            color = TextDisabled,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                itemsIndexed(actions, key = { _, action -> action.id }) { index, action ->
                    ActionRow(
                        index = index + 1,
                        action = action,
                        onDelete = { viewModel.removeAction(action) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddActionDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { type, x, y, endX, endY ->
                viewModel.addManualAction(type, x, y, endX, endY)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GamingCard)
            .border(1.dp, GamingBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = NeonCyan,
            letterSpacing = 1.sp
        )
        content()
    }
}

@Composable
private fun SpeedSelector(current: Float, onChange: (Float) -> Unit) {
    val speeds = listOf(0.5f, 1.0f, 1.5f, 2.0f, 3.0f)
    Column {
        Text("Playback Speed", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            speeds.forEach { speed ->
                val selected = current == speed
                FilterChip(
                    selected = selected,
                    onClick = { onChange(speed) },
                    label = { Text("${speed}x", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                        selectedLabelColor = NeonCyan,
                        containerColor = GamingCardElevated,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        selectedBorderColor = NeonCyan,
                        borderColor = GamingBorder
                    )
                )
            }
        }
    }
}

@Composable
private fun LoopSelector(current: Int, onChange: (Int) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Loop Count", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1f))
            Text(
                text = if (current == -1) "∞" else current.toString(),
                color = NeonPurple,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1, 3, 5, 10, -1).forEach { count ->
                val selected = current == count
                FilterChip(
                    selected = selected,
                    onClick = { onChange(count) },
                    label = { Text(if (count == -1) "∞" else count.toString(), fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonPurple.copy(alpha = 0.2f),
                        selectedLabelColor = NeonPurple,
                        containerColor = GamingCardElevated,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        selectedBorderColor = NeonPurple,
                        borderColor = GamingBorder
                    )
                )
            }
        }
    }
}

@Composable
private fun NumberInput(label: String, value: Long, onChange: (Long) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input
                input.toLongOrNull()?.let { onChange(it.coerceAtLeast(0L)) }
            },
            modifier = Modifier.width(100.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = GamingBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = NeonCyan
            ),
            shape = RoundedCornerShape(6.dp)
        )
    }
}

@Composable
private fun ActionRow(index: Int, action: MacroAction, onDelete: () -> Unit) {
    val (color, icon) = when (action.type) {
        ActionType.TAP -> NeonCyan to Icons.Default.TouchApp
        ActionType.LONG_PRESS -> NeonPurple to Icons.Default.PanTool
        ActionType.SWIPE -> NeonGreen to Icons.Default.SwipeRight
        ActionType.MULTI_TOUCH -> NeonOrange to Icons.Default.Fingerprint
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(GamingCardElevated)
            .border(1.dp, GamingBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("#$index", color = TextDisabled, fontSize = 11.sp, modifier = Modifier.width(28.dp))
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = action.type.name.replace("_", " "),
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = buildString {
                    append("(${action.x.toInt()}, ${action.y.toInt()})")
                    if (action.type == ActionType.SWIPE) append(" → (${action.endX.toInt()}, ${action.endY.toInt()})")
                    append("  ${action.duration}ms")
                    if (action.delayBefore > 0) append("  +${action.delayBefore}ms delay")
                },
                color = TextDisabled,
                fontSize = 10.sp
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(30.dp)
        ) {
            Icon(Icons.Default.Close, null, tint = NeonRed, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AddActionDialog(
    onDismiss: () -> Unit,
    onAdd: (ActionType, Float, Float, Float, Float) -> Unit
) {
    var selectedType by remember { mutableStateOf(ActionType.TAP) }
    var x by remember { mutableStateOf("500") }
    var y by remember { mutableStateOf("1000") }
    var endX by remember { mutableStateOf("800") }
    var endY by remember { mutableStateOf("1000") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GamingCard,
        title = { Text("Add Action", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Action Type", color = TextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionType.values().forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.name.take(5), fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                selectedLabelColor = NeonCyan,
                                containerColor = GamingCardElevated,
                                labelColor = TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedType == type,
                                selectedBorderColor = NeonCyan,
                                borderColor = GamingBorder
                            )
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CoordInput("X", x, { x = it }, Modifier.weight(1f))
                    CoordInput("Y", y, { y = it }, Modifier.weight(1f))
                }

                if (selectedType == ActionType.SWIPE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CoordInput("End X", endX, { endX = it }, Modifier.weight(1f))
                        CoordInput("End Y", endY, { endY = it }, Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(
                        selectedType,
                        x.toFloatOrNull() ?: 500f,
                        y.toFloatOrNull() ?: 1000f,
                        endX.toFloatOrNull() ?: 800f,
                        endY.toFloatOrNull() ?: 1000f
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = GamingBlack)
            ) {
                Text("Add", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CoordInput(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 11.sp) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonCyan,
            unfocusedBorderColor = GamingBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedLabelColor = NeonCyan,
            unfocusedLabelColor = TextSecondary,
            cursorColor = NeonCyan
        ),
        shape = RoundedCornerShape(6.dp)
    )
}

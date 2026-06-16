package com.ggmacro.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ggmacro.app.ui.components.MacroCard
import com.ggmacro.app.ui.components.PermissionBanner
import com.ggmacro.app.ui.theme.*
import com.ggmacro.app.utils.PermissionManager
import com.ggmacro.app.viewmodel.HomeViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToDetail: (Long) -> Unit,
    onCreateNew: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val macros by viewModel.macros.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasOverlay by remember { mutableStateOf(PermissionManager.hasOverlayPermission(context)) }
    var hasAccessibility by remember { mutableStateOf(PermissionManager.hasAccessibilityPermission(context)) }
    var playingMacroId by remember { mutableLongStateOf(-1L) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportMacros(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importMacros(it) } }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) playingMacroId = -1L
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = GamingBlack,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "GG",
                            fontWeight = FontWeight.ExtraBold,
                            color = NeonCyan,
                            fontSize = 22.sp
                        )
                        Text(
                            " Macro",
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            fontSize = 22.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GamingDarkSurface
                ),
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) {
                        Icon(Icons.Default.FileDownload, "Import", tint = TextSecondary)
                    }
                    IconButton(onClick = { exportLauncher.launch("gg_macros_backup.json") }) {
                        Icon(Icons.Default.FileUpload, "Export", tint = TextSecondary)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateNew,
                containerColor = NeonCyan,
                contentColor = GamingBlack,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, "New Macro", modifier = Modifier.size(28.dp))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            PermissionBanner(
                hasOverlay = hasOverlay,
                hasAccessibility = hasAccessibility,
                onRequestOverlay = {
                    PermissionManager.requestOverlayPermission(context)
                },
                onRequestAccessibility = {
                    PermissionManager.requestAccessibilityPermission(context)
                }
            )

            if (!hasOverlay || !hasAccessibility) Spacer(Modifier.height(12.dp))

            if (macros.isEmpty()) {
                EmptyState(onCreateNew = onCreateNew)
            } else {
                Text(
                    text = "${macros.size} Macro${if (macros.size != 1) "s" else ""}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(macros, key = { it.id }) { macro ->
                        MacroCard(
                            macro = macro,
                            isPlaying = playingMacroId == macro.id,
                            onPlay = {
                                playingMacroId = macro.id
                                viewModel.playMacro(macro)
                            },
                            onStop = {
                                viewModel.stopPlayback()
                                playingMacroId = -1L
                            },
                            onEdit = { onNavigateToDetail(macro.id) },
                            onDuplicate = { viewModel.duplicateMacro(macro) },
                            onDelete = { viewModel.deleteMacro(macro) },
                            onOpenOverlay = { viewModel.openOverlay(macro) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onCreateNew: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(NeonCyan.copy(alpha = 0.2f), NeonPurple.copy(alpha = 0.2f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Games,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(44.dp)
                )
            }

            Text(
                text = "No Macros Yet",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Create your first gaming macro to automate touch sequences and dominate the game.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Button(
                onClick = onCreateNew,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = GamingBlack
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Create Macro", fontWeight = FontWeight.Bold)
            }
        }
    }
}

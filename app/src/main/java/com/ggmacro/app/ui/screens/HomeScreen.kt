package com.ggmacro.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
    val triggerRunning by viewModel.triggerRunning.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasOverlay by remember { mutableStateOf(PermissionManager.hasOverlayPermission(context)) }
    var hasAccessibility by remember { mutableStateOf(PermissionManager.hasAccessibilityPermission(context)) }
    var playingMacroId by remember { mutableLongStateOf(-1L) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasOverlay = PermissionManager.hasOverlayPermission(context)
        hasAccessibility = PermissionManager.hasAccessibilityPermission(context)
        viewModel.refreshTriggerState()
    }

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
                        Text("GG", fontWeight = FontWeight.ExtraBold, color = NeonCyan, fontSize = 22.sp)
                        Text(" Macro", fontWeight = FontWeight.ExtraBold, color = TextPrimary, fontSize = 22.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GamingDarkSurface),
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
                onRequestOverlay = { PermissionManager.requestOverlayPermission(context) },
                onRequestAccessibility = { PermissionManager.requestAccessibilityPermission(context) }
            )

            if (!hasOverlay || !hasAccessibility) Spacer(Modifier.height(10.dp))

            // ── Trigger Buttons Card ──────────────────────────────────────
            TriggerButtonCard(
                isRunning = triggerRunning,
                hasPermissions = hasOverlay && hasAccessibility,
                onStart = {
                    if (!hasOverlay) {
                        PermissionManager.requestOverlayPermission(context)
                    } else if (!hasAccessibility) {
                        PermissionManager.requestAccessibilityPermission(context)
                    } else {
                        viewModel.startTriggerButtons()
                    }
                },
                onStop = { viewModel.stopTriggerButtons() }
            )

            Spacer(Modifier.height(12.dp))

            if (macros.isEmpty()) {
                EmptyState(onCreateNew = onCreateNew)
            } else {
                Text(
                    text = "${macros.size} Macro",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(macros, key = { it.id }) { macro ->
                        MacroCard(
                            macro = macro,
                            isPlaying = playingMacroId == macro.id,
                            onPlay = { playingMacroId = macro.id; viewModel.playMacro(macro) },
                            onStop = { viewModel.stopPlayback(); playingMacroId = -1L },
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

// ── Trigger Button Card ────────────────────────────────────────────────────

@Composable
private fun TriggerButtonCard(
    isRunning: Boolean,
    hasPermissions: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val borderColor = if (isRunning) Color(0xFF00E676) else NeonCyan.copy(alpha = 0.4f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF0D1B2A),
                        Color(0xFF0A1628)
                    )
                )
            )
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = if (isRunning) Color(0xFF00E676) else NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Tetik Butonları",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(Modifier.weight(1f))
                if (isRunning) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF00E676).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "● AKTİF",
                            color = Color(0xFF00E676),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = if (isRunning)
                    "Ekranda + butonuna bas → yeni tetik oluştur\nTetik'e bas & tut → otomatik tıkla | Bırak → dur\nBitince aşağıdan DURDUR'a bas"
                else
                    "Oyun içinde istediğin yere otomatik tıklama butonu\nBas & tut = tıklama başlar | Bırak = durur\nSürükleyerek konumlandır, × ile sil",
                color = TextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isRunning) {
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasPermissions) NeonCyan else Color(0xFF455A64),
                            contentColor = if (hasPermissions) GamingBlack else Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(40.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (hasPermissions) "BAŞLAT" else "İZİN GEREKLİ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF5350),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(40.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("DURDUR", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ── Empty state ────────────────────────────────────────────────────────────

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
            Text("Henüz Macro Yok", style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(
                "İlk gaming macronu oluştur ve dokunma dizilerini otomatikleştir.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary, textAlign = TextAlign.Center, lineHeight = 20.sp
            )
            Button(
                onClick = onCreateNew,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = GamingBlack),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Macro Oluştur", fontWeight = FontWeight.Bold)
            }
        }
    }
}

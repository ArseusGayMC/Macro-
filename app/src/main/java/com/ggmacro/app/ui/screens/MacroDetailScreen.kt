package com.ggmacro.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ggmacro.app.ui.theme.*
import com.ggmacro.app.viewmodel.MacroDetailViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: MacroDetailViewModel = hiltViewModel()
) {
    val macro              by viewModel.macro.collectAsStateWithLifecycle()
    val macroName          by viewModel.macroName.collectAsStateWithLifecycle()
    val tapDuration        by viewModel.tapDuration.collectAsStateWithLifecycle()
    val tapDelay           by viewModel.tapDelay.collectAsStateWithLifecycle()
    val holdThreshold      by viewModel.holdThreshold.collectAsStateWithLifecycle()
    val isTriggerActive    by viewModel.isTriggerActive.collectAsStateWithLifecycle()
    val a11yRunning        by viewModel.accessibilityRunning.collectAsStateWithLifecycle()
    val snackbarHostState  = remember { SnackbarHostState() }

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
                        text = if (macro == null) "Yeni Macro" else "Macro Ayarları",
                        fontWeight = FontWeight.Bold, color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Geri", tint = TextSecondary)
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.saveMacro() },
                        colors = ButtonDefaults.textButtonColors(contentColor = NeonCyan)
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Kaydet", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GamingDarkSurface)
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Erişilebilirlik durumu ──────────────────────────────────
            if (!a11yRunning) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(NeonRed.copy(alpha = 0.12f))
                        .border(1.dp, NeonRed.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Warning, null, tint = NeonRed, modifier = Modifier.size(20.dp))
                    Column {
                        Text("Erişilebilirlik Servisi Kapalı", fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, color = NeonRed)
                        Text("Ayarlar → Erişilebilirlik → GG Macro Service → Aç",
                            fontSize = 11.sp, color = NeonRed.copy(alpha = 0.75f))
                    }
                }
            }

            // ── Macro adı ───────────────────────────────────────────────
            Section(title = "Macro Adı") {
                OutlinedTextField(
                    value = macroName,
                    onValueChange = viewModel::setName,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Macro adı gir", color = TextDisabled) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = NeonCyan,
                        unfocusedBorderColor = GamingBorder,
                        focusedTextColor     = TextPrimary,
                        unfocusedTextColor   = TextPrimary,
                        cursorColor          = NeonCyan
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }

            // ── Tıklama ayarları ────────────────────────────────────────
            Section(title = "Tıklama Ayarları") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    NumField(
                        label = "Tek Tıklama Süresi (ms)",
                        hint  = "Her tıklama ne kadar sürsün?  → 50",
                        value = tapDuration,
                        onChange = viewModel::setTapDuration
                    )
                    NumField(
                        label = "Tıklamalar Arası Bekleme (ms)",
                        hint  = "İki tıklama arasındaki boşluk → 50",
                        value = tapDelay,
                        onChange = viewModel::setTapDelay
                    )
                    NumField(
                        label = "Basılı Tutma Eşiği (ms)",
                        hint  = "Ne kadar tut → tıklama başlasın? → 400",
                        value = holdThreshold,
                        onChange = viewModel::setHoldThreshold
                    )
                }
            }

            // ── Trigger button ──────────────────────────────────────────
            Section(title = "Tetik Butonu") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                    if (isTriggerActive) {
                        Button(
                            onClick = { viewModel.stopTriggerButton() },
                            modifier = Modifier.fillMaxWidth(),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = NeonRed.copy(alpha = 0.15f),
                                contentColor   = NeonRed
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Overlay'i Kaldır", fontWeight = FontWeight.SemiBold)
                        }

                        // Kullanım notu
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonCyan.copy(alpha = 0.06f))
                                .border(1.dp, NeonCyan.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FiberManualRecord, null,
                                    tint = NeonCyan, modifier = Modifier.size(10.dp))
                                Text("Mavi daire = tetik butonu",
                                    fontSize = 12.sp, color = TextSecondary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, null,
                                    tint = NeonRed.copy(alpha = 0.8f), modifier = Modifier.size(10.dp))
                                Text("Kırmızı artı = hedef (buraya tıklar) — sürükle",
                                    fontSize = 12.sp, color = TextSecondary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PanTool, null,
                                    tint = NeonGreen, modifier = Modifier.size(10.dp))
                                Text("Mavi butona basılı tut → tıklama başlar, bırak → durur",
                                    fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                    } else {
                        Button(
                            onClick  = { viewModel.startTriggerButton() },
                            enabled  = a11yRunning,
                            modifier = Modifier.fillMaxWidth(),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor    = NeonGreen.copy(alpha = 0.15f),
                                contentColor      = NeonGreen,
                                disabledContainerColor = GamingCard,
                                disabledContentColor   = TextDisabled
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.TouchApp, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (a11yRunning) "Overlay'i Ekrana Koy"
                                else "Önce Servis'i Aç",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Yardımcı composable'lar ──────────────────────────────────────────────

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GamingCard)
            .border(1.dp, GamingBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            color = NeonCyan, letterSpacing = 0.8.sp)
        content()
    }
}

@Composable
private fun NumField(label: String, hint: String, value: Long, onChange: (Long) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    text = input
                    input.toLongOrNull()?.let { onChange(it) }
                },
                modifier = Modifier.width(110.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = NeonCyan,
                    unfocusedBorderColor = GamingBorder,
                    focusedTextColor     = TextPrimary,
                    unfocusedTextColor   = TextPrimary,
                    cursorColor          = NeonCyan
                ),
                shape = RoundedCornerShape(6.dp)
            )
            Text(hint, fontSize = 11.sp, color = TextDisabled,
                modifier = Modifier.weight(1f), lineHeight = 14.sp)
        }
    }
}

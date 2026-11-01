@file:OptIn(ExperimentalMaterial3Api::class)

package com.minhasacoes.app.ui.wallet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minhasacoes.app.app
import com.minhasacoes.app.data.db.AssetEntity
import com.minhasacoes.app.data.db.MaState
import com.minhasacoes.app.data.db.WalletWithAssets
import com.minhasacoes.app.ui.components.BreadthBar
import com.minhasacoes.app.ui.components.BreadthLegend
import com.minhasacoes.app.ui.components.ConfirmDialog
import com.minhasacoes.app.ui.components.WalletDialog
import com.minhasacoes.app.ui.components.changeColor
import com.minhasacoes.app.ui.theme.AppTheme
import com.minhasacoes.app.util.dayChange
import com.minhasacoes.app.util.distanceToSma
import com.minhasacoes.app.util.money
import com.minhasacoes.app.util.pct
import com.minhasacoes.app.util.summary
import com.minhasacoes.app.util.ticker
import com.minhasacoes.app.util.updatedLabel
import kotlinx.coroutines.launch

private enum class WalletDialogKind { NONE, ADD, RENAME, PERIOD, DELETE }

@Composable
fun WalletScreen(walletId: Long, onBack: () -> Unit) {
    val app = LocalContext.current.app
    val repo = app.repository
    val flow = remember(walletId) { repo.observeWallet(walletId) }
    val data by flow.collectAsStateWithLifecycle(initialValue = null)
    val refreshing by repo.refreshing.collectAsStateWithLifecycle()
    val lastUpdate by repo.lastUpdate.collectAsStateWithLifecycle()

    var dialog by remember { mutableStateOf(WalletDialogKind.NONE) }
    var menuOpen by remember { mutableStateOf(false) }
    var selectedAssetId by rememberSaveable { mutableStateOf<Long?>(null) }

    val wallet = data?.wallet

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        wallet?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Opções")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Renomear") },
                                onClick = { menuOpen = false; dialog = WalletDialogKind.RENAME },
                            )
                            DropdownMenuItem(
                                text = { Text("Alterar média padrão") },
                                onClick = { menuOpen = false; dialog = WalletDialogKind.PERIOD },
                            )
                            DropdownMenuItem(
                                text = { Text("Excluir carteira", color = AppTheme.market.down) },
                                onClick = { menuOpen = false; dialog = WalletDialogKind.DELETE },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            if (wallet != null) {
                ExtendedFloatingActionButton(
                    onClick = { dialog = WalletDialogKind.ADD },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Adicionar ação") },
                    shape = RoundedCornerShape(18.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        val current = data
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { app.appScope.launch { repo.refreshAll() } },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (current != null) {
                val assets = remember(current.assets) { current.assets.sortedBy { it.ticker } }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "summary") { SummaryCard(current, lastUpdate) }
                    if (assets.isEmpty()) {
                        item(key = "empty") { EmptyAssets() }
                    } else {
                        item(key = "label") {
                            Text(
                                "Ações",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp),
                            )
                        }
                        items(assets, key = { it.id }) { asset ->
                            AssetRow(asset, onClick = { selectedAssetId = asset.id })
                        }
                    }
                }
            }
        }
    }

    // ----- Diálogos -----

    if (wallet != null) {
        when (dialog) {
            WalletDialogKind.ADD -> AddAssetDialog(
                defaultPeriod = wallet.defaultPeriod,
                onDismiss = { dialog = WalletDialogKind.NONE },
                onAdd = { ticker, period -> repo.addAsset(wallet.id, ticker, period) },
            )

            WalletDialogKind.RENAME -> WalletDialog(
                title = "Renomear carteira",
                confirmLabel = "Salvar",
                initialName = wallet.name,
                showPeriod = false,
                onDismiss = { dialog = WalletDialogKind.NONE },
                onConfirm = { name, _ ->
                    dialog = WalletDialogKind.NONE
                    app.appScope.launch { repo.renameWallet(wallet.id, name) }
                },
            )

            WalletDialogKind.PERIOD -> WalletDialog(
                title = "Média padrão",
                confirmLabel = "Aplicar",
                initialPeriod = wallet.defaultPeriod,
                showName = false,
                periodHint = "Aplica a todas as ações desta carteira",
                onDismiss = { dialog = WalletDialogKind.NONE },
                onConfirm = { _, period ->
                    dialog = WalletDialogKind.NONE
                    app.appScope.launch { repo.setWalletPeriod(wallet.id, period) }
                },
            )

            WalletDialogKind.DELETE -> ConfirmDialog(
                title = "Excluir carteira?",
                message = "“${wallet.name}” e suas ações serão removidas. Essa ação não pode ser desfeita.",
                confirmLabel = "Excluir",
                onDismiss = { dialog = WalletDialogKind.NONE },
                onConfirm = {
                    dialog = WalletDialogKind.NONE
                    val id = wallet.id
                    onBack()
                    app.appScope.launch { repo.deleteWallet(id) }
                },
            )

            WalletDialogKind.NONE -> Unit
        }
    }

    val selected = data?.assets?.firstOrNull { it.id == selectedAssetId }
    if (selected != null) {
        AssetSheet(
            asset = selected,
            onDismiss = { selectedAssetId = null },
            onSave = { period, alert ->
                selectedAssetId = null
                app.appScope.launch { repo.updateAsset(selected.id, period, alert) }
            },
            onRemove = {
                selectedAssetId = null
                app.appScope.launch { repo.removeAsset(selected.id) }
            },
        )
    }
}

@Composable
private fun SummaryCard(data: WalletWithAssets, lastUpdate: Long) {
    val s = data.summary()
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, AppTheme.market.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Variação média do dia",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                pct(s.dayChange),
                style = MaterialTheme.typography.displaySmall,
                color = changeColor(s.dayChange),
            )
            Spacer(Modifier.height(16.dp))
            BreadthBar(above = s.above, below = s.below)
            Spacer(Modifier.height(10.dp))
            BreadthLegend(above = s.above, below = s.below, trailing = "MM${data.wallet.defaultPeriod}")
            Spacer(Modifier.height(12.dp))
            Text(
                updatedLabel(lastUpdate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AssetRow(asset: AssetEntity, onClick: () -> Unit) {
    val market = AppTheme.market
    val stateColor = when (asset.state) {
        MaState.ABOVE -> market.up
        MaState.BELOW -> market.down
        else -> market.neutral
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, market.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrendBadge(asset.state, stateColor)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(asset.ticker, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        asset.name ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        money(asset.price, asset.currency),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val change = asset.dayChange
                    Text(pct(change), style = MaterialTheme.typography.bodySmall, color = changeColor(change))
                }
            }
            Spacer(Modifier.height(12.dp))
            StatusStrip(asset, stateColor)
        }
    }
}

@Composable
private fun TrendBadge(state: String?, color: Color) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            MaState.ABOVE -> Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Em alta", tint = color)
            MaState.BELOW -> Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Em baixa", tint = color)
            else -> Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun StatusStrip(asset: AssetEntity, color: Color) {
    val (label, detail) = when {
        asset.price == null -> (asset.error ?: "Aguardando cotação") to null
        asset.sma == null -> "Histórico insuficiente para MM${asset.period}" to null
        asset.state == MaState.BELOW -> "Abaixo da MM${asset.period}" to pct(asset.distanceToSma)
        else -> "Acima da MM${asset.period}" to pct(asset.distanceToSma)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (detail != null) {
            Text(
                "${money(asset.sma, asset.currency)}  ·  $detail",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyAssets() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nenhuma ação ainda", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Toque em “Adicionar ação” e digite o ticker, como PETR4, VALE3 ou AAPL.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

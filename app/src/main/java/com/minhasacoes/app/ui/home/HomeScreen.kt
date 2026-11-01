@file:OptIn(ExperimentalMaterial3Api::class)

package com.minhasacoes.app.ui.home

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minhasacoes.app.app
import com.minhasacoes.app.data.db.WalletWithAssets
import com.minhasacoes.app.ui.components.BreadthBar
import com.minhasacoes.app.ui.components.BreadthLegend
import com.minhasacoes.app.ui.components.Chip
import com.minhasacoes.app.ui.components.WalletDialog
import com.minhasacoes.app.ui.components.changeColor
import com.minhasacoes.app.ui.theme.AppTheme
import com.minhasacoes.app.util.pct
import com.minhasacoes.app.util.summary
import com.minhasacoes.app.util.updatedLabel
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenWallet: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val app = LocalContext.current.app
    val repo = app.repository
    val walletsFlow = remember { repo.observeWallets() }
    val wallets by walletsFlow.collectAsStateWithLifecycle(initialValue = null)
    val refreshing by repo.refreshing.collectAsStateWithLifecycle()
    val lastUpdate by repo.lastUpdate.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }

    // Ao abrir o app, atualiza se os dados tiverem mais de 30 minutos.
    LaunchedEffect(Unit) {
        if (System.currentTimeMillis() - repo.lastUpdate.value > 30 * 60 * 1000L) {
            app.appScope.launch { repo.refreshAll() }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nova carteira") },
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { app.appScope.launch { repo.refreshAll() } },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "header") {
                    Header(lastUpdate = lastUpdate, onOpenSettings = onOpenSettings)
                }
                val list = wallets
                if (list != null && list.isEmpty()) {
                    item(key = "empty") { EmptyState() }
                } else if (list != null) {
                    items(list, key = { it.wallet.id }) { w ->
                        WalletCard(data = w, onClick = { onOpenWallet(w.wallet.id) })
                    }
                }
            }
        }
    }

    if (showCreate) {
        WalletDialog(
            title = "Nova carteira",
            confirmLabel = "Criar",
            onDismiss = { showCreate = false },
            onConfirm = { name, period ->
                showCreate = false
                app.appScope.launch { repo.createWallet(name, period) }
            },
        )
    }
}

@Composable
private fun Header(lastUpdate: Long, onOpenSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Minhas carteiras", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                updatedLabel(lastUpdate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = "Configurações",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WalletCard(data: WalletWithAssets, onClick: () -> Unit) {
    val s = data.summary()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, AppTheme.market.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    data.wallet.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Chip("MM${data.wallet.defaultPeriod}")
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    pct(s.dayChange),
                    style = MaterialTheme.typography.displaySmall,
                    color = changeColor(s.dayChange),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "hoje",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 7.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            if (s.total == 0) {
                Text(
                    "Toque para adicionar ações",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                BreadthBar(above = s.above, below = s.below)
                Spacer(Modifier.height(10.dp))
                BreadthLegend(
                    above = s.above,
                    below = s.below,
                    trailing = if (s.total == 1) "1 ação" else "${s.total} ações",
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 72.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("Crie sua primeira carteira", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Adicione ações e acompanhe se estão acima ou abaixo da média móvel que você escolher.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

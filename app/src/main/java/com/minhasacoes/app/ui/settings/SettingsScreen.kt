@file:OptIn(ExperimentalMaterial3Api::class)

package com.minhasacoes.app.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minhasacoes.app.app
import com.minhasacoes.app.notify.Notifier
import com.minhasacoes.app.ui.components.SmallSpinner
import com.minhasacoes.app.ui.theme.AppTheme
import com.minhasacoes.app.util.updatedLabel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val repo = app.repository
    val notifyAbove by repo.notifyAbove.collectAsStateWithLifecycle()
    val refreshing by repo.refreshing.collectAsStateWithLifecycle()
    val lastUpdate by repo.lastUpdate.collectAsStateWithLifecycle()

    var canNotify by remember { mutableStateOf(Notifier.canNotify(context)) }

    // Reavalia a permissão ao voltar das configurações do sistema.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) canNotify = Notifier.canNotify(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        canNotify = granted && Notifier.canNotify(context)
        if (!granted) openNotificationSettings(context)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Configurações", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Notificações", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (canNotify) "Permitidas" else "Bloqueadas — você não receberá alertas",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (canNotify) AppTheme.market.up else AppTheme.market.down,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    FilledTonalButton(
                        onClick = {
                            when {
                                canNotify -> Notifier.sendTest(context)
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else -> openNotificationSettings(context)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                    ) { Text(if (canNotify) "Testar" else "Permitir") }
                }
            }

            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Avisar quando voltar acima", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Além da queda abaixo da média, avisar quando a ação voltar a ficar acima.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = notifyAbove, onCheckedChange = { repo.setNotifyAbove(it) })
                }
            }

            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Atualização", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Automática a cada hora. ${updatedLabel(lastUpdate)}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    FilledTonalButton(
                        onClick = { app.appScope.launch { repo.refreshAll() } },
                        enabled = !refreshing,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (refreshing) SmallSpinner() else Text("Agora")
                    }
                }
            }

            Text(
                "Cotações gratuitas do Yahoo Finance, com atraso de até 15 minutos. " +
                    "A média móvel simples usa os fechamentos diários, com o preço atual como último valor. " +
                    "Não é recomendação de investimento.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, AppTheme.market.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

private fun openNotificationSettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // sem tela de configurações disponível
    }
}

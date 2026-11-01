@file:OptIn(ExperimentalMaterial3Api::class)

package com.minhasacoes.app.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.minhasacoes.app.data.db.AssetEntity
import com.minhasacoes.app.ui.components.MAX_PERIOD
import com.minhasacoes.app.ui.components.MIN_PERIOD
import com.minhasacoes.app.ui.components.PeriodPresets
import com.minhasacoes.app.ui.components.SmallSpinner
import com.minhasacoes.app.ui.components.changeColor
import com.minhasacoes.app.ui.components.parsePeriod
import com.minhasacoes.app.ui.theme.AppTheme
import com.minhasacoes.app.util.distanceToSma
import com.minhasacoes.app.util.money
import com.minhasacoes.app.util.pct
import com.minhasacoes.app.util.ticker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AddAssetDialog(
    defaultPeriod: Int,
    onDismiss: () -> Unit,
    onAdd: suspend (ticker: String, period: Int) -> Unit,
) {
    var ticker by remember { mutableStateOf("") }
    var periodText by remember { mutableStateOf(defaultPeriod.toString()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val period = parsePeriod(periodText)
    val valid = ticker.isNotBlank() && period != null && !loading

    fun submit() {
        if (!valid || period == null) return
        loading = true
        error = null
        scope.launch {
            try {
                onAdd(ticker, period)
                onDismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Não foi possível adicionar"
                loading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Adicionar ação") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = ticker,
                    onValueChange = { ticker = it.uppercase().filter { c -> !c.isWhitespace() }.take(16); error = null },
                    label = { Text("Ticker") },
                    placeholder = { Text("PETR4, VALE3, AAPL…") },
                    singleLine = true,
                    enabled = !loading,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = periodText,
                    onValueChange = { periodText = it.filter(Char::isDigit).take(3) },
                    label = { Text("Média móvel (períodos)") },
                    supportingText = { Text("De $MIN_PERIOD a $MAX_PERIOD pregões") },
                    isError = periodText.isNotEmpty() && period == null,
                    singleLine = true,
                    enabled = !loading,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                PeriodPresets(onPick = { periodText = it.toString() })
                val err = error
                if (err != null) {
                    Text(err, color = AppTheme.market.down, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = valid) {
                if (loading) SmallSpinner() else Text("Adicionar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancelar") }
        },
    )
}

@Composable
fun AssetSheet(
    asset: AssetEntity,
    onDismiss: () -> Unit,
    onSave: (period: Int, alertEnabled: Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var periodText by remember(asset.id) { mutableStateOf(asset.period.toString()) }
    var alert by remember(asset.id) { mutableStateOf(asset.alertEnabled) }
    val period = parsePeriod(periodText)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .imePadding()
                .padding(bottom = 16.dp)
        ) {
            Text(asset.ticker, style = MaterialTheme.typography.headlineSmall)
            Text(
                asset.name ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("Preço", money(asset.price, asset.currency), Modifier.weight(1f))
                Stat("MM${asset.period}", money(asset.sma, asset.currency), Modifier.weight(1f))
                val dist = asset.distanceToSma
                Stat("Distância", pct(dist), Modifier.weight(1f), valueColor = changeColor(dist))
            }

            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = periodText,
                onValueChange = { periodText = it.filter(Char::isDigit).take(3) },
                label = { Text("Período da média móvel") },
                isError = periodText.isNotEmpty() && period == null,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            PeriodPresets(onPick = { periodText = it.toString() })

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Alerta de cruzamento", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Notificar quando ficar abaixo da média",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = alert,
                    onCheckedChange = { alert = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                )
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { if (period != null) onSave(period, alert) },
                enabled = period != null,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text("Salvar") }
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = onRemove,
                colors = ButtonDefaults.textButtonColors(contentColor = AppTheme.market.down),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Remover da carteira") }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
            maxLines = 1,
        )
    }
}

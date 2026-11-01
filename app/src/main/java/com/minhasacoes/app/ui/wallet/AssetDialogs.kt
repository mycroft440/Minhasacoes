@file:OptIn(ExperimentalMaterial3Api::class)

package com.minhasacoes.app.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.minhasacoes.app.data.MaConfig
import com.minhasacoes.app.data.api.SymbolSuggestion
import com.minhasacoes.app.data.calc.Indicators
import com.minhasacoes.app.data.calc.MaState
import com.minhasacoes.app.data.calc.MaType
import com.minhasacoes.app.data.db.AssetEntity
import com.minhasacoes.app.ui.components.MAX_PERIOD
import com.minhasacoes.app.ui.components.MIN_PERIOD
import com.minhasacoes.app.ui.components.MaTypeSelector
import com.minhasacoes.app.ui.components.PeriodPresets
import com.minhasacoes.app.ui.components.SmallSpinner
import com.minhasacoes.app.ui.components.StockLogo
import com.minhasacoes.app.ui.components.TrendChart
import com.minhasacoes.app.ui.components.changeColor
import com.minhasacoes.app.ui.components.parsePeriod
import com.minhasacoes.app.ui.theme.AppTheme
import com.minhasacoes.app.util.distanceToSma
import com.minhasacoes.app.util.money
import com.minhasacoes.app.util.pct
import com.minhasacoes.app.util.ticker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AddAssetDialog(
    defaultPeriod: Int,
    defaultType: String,
    onDismiss: () -> Unit,
    onSearch: suspend (query: String) -> List<SymbolSuggestion>,
    onAdd: suspend (ticker: String, config: MaConfig) -> Unit,
) {
    var ticker by remember { mutableStateOf("") }
    var periodText by remember { mutableStateOf(defaultPeriod.toString()) }
    var maType by remember { mutableStateOf(MaType.of(defaultType)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var suggestions by remember { mutableStateOf<List<SymbolSuggestion>>(emptyList()) }
    var picked by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val period = parsePeriod(periodText)
    val valid = ticker.isNotBlank() && period != null && !loading

    // Autocompletar com espera curta entre as teclas.
    LaunchedEffect(ticker) {
        val query = ticker.trim()
        if (query.length < 2 || query == picked) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        suggestions = try {
            onSearch(query).take(5)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun submit() {
        if (!valid || period == null) return
        loading = true
        error = null
        suggestions = emptyList()
        scope.launch {
            try {
                onAdd(ticker, MaConfig(type = maType, period = period, fastPeriod = null))
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
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = ticker,
                    onValueChange = {
                        ticker = it.uppercase().filter { c -> !c.isWhitespace() }.take(16)
                        picked = null
                        error = null
                    },
                    label = { Text("Ticker ou nome") },
                    placeholder = { Text("PETR4, Vale, AAPL…") },
                    singleLine = true,
                    enabled = !loading,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (suggestions.isNotEmpty()) {
                    SuggestionList(suggestions) { s ->
                        val display = s.symbol.removeSuffix(".SA")
                        picked = if (s.symbol.endsWith(".SA")) display else s.symbol
                        ticker = picked.orEmpty()
                        suggestions = emptyList()
                    }
                }
                MaTypeSelector(selected = maType, onSelect = { maType = it })
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
private fun SuggestionList(items: List<SymbolSuggestion>, onPick: (SymbolSuggestion) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        items.forEach { s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(s) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StockLogo(s.symbol, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        s.symbol.removeSuffix(".SA"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        s.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val exchange = s.exchange
                if (exchange != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        exchange,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun AssetSheet(
    asset: AssetEntity,
    onDismiss: () -> Unit,
    onSave: (config: MaConfig, alertEnabled: Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var periodText by remember(asset.id) { mutableStateOf(asset.period.toString()) }
    var maType by remember(asset.id) { mutableStateOf(MaType.of(asset.maType)) }
    var crossMode by remember(asset.id) { mutableStateOf(asset.fastPeriod != null) }
    var fastText by remember(asset.id) { mutableStateOf((asset.fastPeriod ?: 9).toString()) }
    var alert by remember(asset.id) { mutableStateOf(asset.alertEnabled) }

    val period = parsePeriod(periodText)
    val fast = parsePeriod(fastText)
    val fastValid = !crossMode || (fast != null && period != null && fast < period)
    val valid = period != null && fastValid

    val market = AppTheme.market
    val stateColor = when (asset.state) {
        MaState.ABOVE -> market.up
        MaState.BELOW -> market.down
        else -> market.neutral
    }
    val closes = remember(asset.chartCloses) { Indicators.decode(asset.chartCloses) }
    val maLine = remember(asset.chartMa) { Indicators.decode(asset.chartMa) }
    val fastLine = remember(asset.chartFast) { asset.chartFast?.let { Indicators.decode(it) } }
    val slowLabel = MaType.label(asset.maType, asset.period)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StockLogo(asset.symbol, size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(asset.ticker, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        asset.name ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("Preço", money(asset.price, asset.currency), Modifier.weight(1f))
                Stat(slowLabel, money(asset.sma, asset.currency), Modifier.weight(1f))
                val dist = asset.distanceToSma
                Stat("Distância", pct(dist), Modifier.weight(1f), valueColor = changeColor(dist))
            }

            if (closes.size >= 2) {
                Spacer(Modifier.height(18.dp))
                TrendChart(
                    closes = closes,
                    ma = maLine,
                    fast = fastLine,
                    priceColor = stateColor,
                    maColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    fastColor = MaterialTheme.colorScheme.primary,
                    priceWidth = 2.2.dp,
                    maWidth = 1.6.dp,
                    fill = true,
                    showLastDot = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                )
                Spacer(Modifier.height(8.dp))
                ChartLegend(
                    priceColor = stateColor,
                    maColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    maLabel = slowLabel,
                    fastColor = MaterialTheme.colorScheme.primary,
                    fastLabel = asset.fastPeriod?.let { MaType.label(asset.maType, it) },
                    days = closes.size,
                )
            }

            Spacer(Modifier.height(24.dp))
            Text("Média móvel", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            MaTypeSelector(selected = maType, onSelect = { maType = it })
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = periodText,
                onValueChange = { periodText = it.filter(Char::isDigit).take(3) },
                label = { Text(if (crossMode) "Média lenta (períodos)" else "Período da média") },
                isError = periodText.isNotEmpty() && period == null,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            PeriodPresets(onPick = { periodText = it.toString() })

            Spacer(Modifier.height(18.dp))
            SwitchRow(
                title = "Cruzamento de médias",
                subtitle = "Comparar uma média rápida com a média lenta, em vez do preço",
                checked = crossMode,
                onChange = { crossMode = it },
            )
            if (crossMode) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = fastText,
                    onValueChange = { fastText = it.filter(Char::isDigit).take(3) },
                    label = { Text("Média rápida (períodos)") },
                    supportingText = { Text("Menor que a média lenta") },
                    isError = !fastValid,
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(14.dp))
            SwitchRow(
                title = "Alerta de cruzamento",
                subtitle = if (crossMode) "Notificar quando a média rápida cruzar para baixo"
                else "Notificar quando ficar abaixo da média",
                checked = alert,
                onChange = { alert = it },
            )

            Spacer(Modifier.height(26.dp))
            Button(
                onClick = {
                    if (period != null && fastValid) {
                        onSave(MaConfig(maType, period, if (crossMode) fast else null), alert)
                    }
                },
                enabled = valid,
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
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChartLegend(
    priceColor: Color,
    maColor: Color,
    maLabel: String,
    fastColor: Color,
    fastLabel: String?,
    days: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendItem(priceColor, "Preço")
        if (fastLabel != null) LegendItem(fastColor, fastLabel)
        LegendItem(maColor, maLabel)
        Spacer(Modifier.weight(1f))
        Text(
            "$days pregões",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            Modifier
                .width(12.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

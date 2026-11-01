@file:OptIn(ExperimentalMaterial3Api::class)

package com.minhasacoes.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.minhasacoes.app.data.calc.MaType
import com.minhasacoes.app.ui.theme.AppTheme

const val MIN_PERIOD = 2
const val MAX_PERIOD = 500

fun parsePeriod(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in MIN_PERIOD..MAX_PERIOD }

@Composable
fun changeColor(value: Double?): Color = when {
    value == null || value == 0.0 -> MaterialTheme.colorScheme.onSurfaceVariant
    value > 0 -> AppTheme.market.up
    else -> AppTheme.market.down
}

/** Barra proporcional: ativos acima x abaixo da média. */
@Composable
fun BreadthBar(above: Int, below: Int, modifier: Modifier = Modifier) {
    val total = above + below
    val target = if (total == 0) 0f else above.toFloat() / total
    val fraction by animateFloatAsState(target, tween(600), label = "breadth")
    val market = AppTheme.market
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (total == 0) MaterialTheme.colorScheme.surfaceVariant else market.down)
    ) {
        if (total > 0 && fraction > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(3.dp))
                    .background(market.up)
            )
        }
    }
}

@Composable
fun BreadthLegend(above: Int, below: Int, trailing: String?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Dot(AppTheme.market.up)
        Spacer(Modifier.width(6.dp))
        Text("$above acima", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(14.dp))
        Dot(AppTheme.market.down)
        Spacer(Modifier.width(6.dp))
        Text("$below abaixo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Dot(color: Color, size: Int = 7) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Diálogo com nome + período (criar carteira). */
@Composable
fun WalletDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    initialPeriod: Int = 20,
    showName: Boolean = true,
    showPeriod: Boolean = true,
    initialType: String = MaType.SMA,
    periodHint: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, period: Int, maType: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var periodText by remember { mutableStateOf(initialPeriod.toString()) }
    var maType by remember { mutableStateOf(MaType.of(initialType)) }
    val period = parsePeriod(periodText)
    val valid = (!showName || name.isNotBlank()) && (!showPeriod || period != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (showName) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        label = { Text("Nome") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = if (showPeriod) ImeAction.Next else ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (showPeriod) {
                    MaTypeSelector(selected = maType, onSelect = { maType = it })
                    OutlinedTextField(
                        value = periodText,
                        onValueChange = { periodText = it.filter(Char::isDigit).take(3) },
                        label = { Text("Média móvel (períodos)") },
                        supportingText = {
                            Text(periodHint ?: "De $MIN_PERIOD a $MAX_PERIOD pregões")
                        },
                        isError = periodText.isNotEmpty() && period == null,
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PeriodPresets(onPick = { periodText = it.toString() })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, period ?: initialPeriod, maType) },
                enabled = valid,
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun PeriodPresets(onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(9, 21, 50, 200).forEach { p ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onPick(p) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text("$p", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Diálogo de confirmação simples. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = AppTheme.market.down)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun SmallSpinner(color: Color = MaterialTheme.colorScheme.primary) {
    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = color)
}

/** Escolha entre média simples e exponencial. */
@Composable
fun MaTypeSelector(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val options = listOf(MaType.SMA to "Simples", MaType.EMA to "Exponencial")
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (type, label) ->
            SegmentedButton(
                selected = MaType.of(selected) == type,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) { Text(label) }
        }
    }
}

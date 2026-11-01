package com.minhasacoes.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.minhasacoes.app.data.calc.Monthly
import com.minhasacoes.app.ui.theme.AppTheme
import com.minhasacoes.app.util.pct
import java.time.YearMonth

private val MONTHS = listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")

/**
 * Rentabilidade mês a mês de um ano, com o acumulado do ano e setas para trocar de ano.
 * [bordered] desenha o cartão (lista da carteira); sem borda para uso dentro do detalhe.
 */
@Composable
fun MonthlyReturnsCard(
    returns: Map<YearMonth, Double>,
    title: String,
    modifier: Modifier = Modifier,
    bordered: Boolean = true,
) {
    val current = YearMonth.now()
    val years = remember(returns) { Monthly.years(returns).ifEmpty { listOf(current.year) } }
    var year by remember(returns) { mutableIntStateOf(if (current.year in years) current.year else years.last()) }
    val total = Monthly.yearTotal(returns, year)

    val content: @Composable () -> Unit = {
        Column(Modifier.padding(if (bordered) 20.dp else 0.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            pct(total),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = changeColor(total),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "no ano",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                }
                IconButton(
                    onClick = { year -= 1 },
                    enabled = year > years.first(),
                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Ano anterior") }
                Text("$year", style = MaterialTheme.typography.titleMedium)
                IconButton(
                    onClick = { year += 1 },
                    enabled = year < maxOf(years.last(), current.year),
                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Próximo ano") }
            }
            Spacer(Modifier.height(12.dp))
            for (row in 0 until 4) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (col in 0 until 3) {
                        val monthIndex = row * 3 + col
                        MonthCell(
                            label = MONTHS[monthIndex],
                            value = returns[YearMonth.of(year, monthIndex + 1)],
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (row < 3) Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (bordered) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, AppTheme.market.cardBorder),
            modifier = modifier.fillMaxWidth(),
        ) { content() }
    } else {
        Column(modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun MonthCell(label: String, value: Double?, modifier: Modifier = Modifier) {
    val color = changeColor(value)
    val tint = when {
        value == null || value == 0.0 -> MaterialTheme.colorScheme.surfaceVariant
        else -> color.copy(alpha = 0.12f)
    }
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tint)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (value == null) "—" else pct(value),
            style = MaterialTheme.typography.labelLarge,
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else color,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

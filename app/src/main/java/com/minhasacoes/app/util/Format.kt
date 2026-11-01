package com.minhasacoes.app.util

import com.minhasacoes.app.data.db.AssetEntity
import com.minhasacoes.app.data.db.MaState
import com.minhasacoes.app.data.db.WalletWithAssets
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

private val BR: Locale = Locale.forLanguageTag("pt-BR")

fun money(value: Double?, currency: String?): String {
    if (value == null || value.isNaN()) return "—"
    val nf = NumberFormat.getCurrencyInstance(BR)
    try {
        nf.currency = Currency.getInstance(currency ?: "BRL")
    } catch (e: Exception) {
        // moeda desconhecida: mantém BRL
    }
    nf.minimumFractionDigits = 2
    nf.maximumFractionDigits = 2
    return nf.format(value)
}

fun pct(value: Double?): String =
    if (value == null || value.isNaN()) "—" else String.format(BR, "%+.2f%%", value)

/** Ticker para exibição (sem o sufixo da B3). */
val AssetEntity.ticker: String get() = symbol.removeSuffix(".SA")

/** Variação do dia em %. */
val AssetEntity.dayChange: Double?
    get() {
        val p = price ?: return null
        val prev = prevClose ?: return null
        return if (prev > 0) (p / prev - 1.0) * 100.0 else null
    }

/** Distância do preço até a média em %. */
val AssetEntity.distanceToSma: Double?
    get() {
        val p = price ?: return null
        val m = sma ?: return null
        return if (m > 0) (p / m - 1.0) * 100.0 else null
    }

data class WalletSummary(
    val dayChange: Double?,
    val above: Int,
    val below: Int,
    val total: Int,
)

/** Resumo da carteira: variação do dia = média simples das variações de todas as ações. */
fun WalletWithAssets.summary(): WalletSummary {
    val changes = assets.mapNotNull { it.dayChange }
    return WalletSummary(
        dayChange = if (changes.isEmpty()) null else changes.average(),
        above = assets.count { it.state == MaState.ABOVE },
        below = assets.count { it.state == MaState.BELOW },
        total = assets.size,
    )
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm", BR)
private val DATE_TIME = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm", BR)

fun updatedLabel(millis: Long): String {
    if (millis <= 0L) return "Ainda não atualizado"
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(millis).atZone(zone)
    return if (dt.toLocalDate() == LocalDate.now(zone)) {
        "Atualizado hoje às ${dt.format(TIME)}"
    } else {
        "Atualizado em ${dt.format(DATE_TIME)}"
    }
}

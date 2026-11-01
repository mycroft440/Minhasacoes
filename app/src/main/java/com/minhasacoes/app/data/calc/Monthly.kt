package com.minhasacoes.app.data.calc

import java.time.YearMonth
import java.util.Locale

/** Fechamento de um mês (o último pregão do mês, ou o preço atual no mês corrente). */
data class MonthClose(val month: YearMonth, val close: Double)

/** Rentabilidade mensal (variação do preço de fechamento mês a mês). Cálculos puros, testados. */
object Monthly {

    /**
     * Variação de cada mês: fechamento do mês / fechamento do mês anterior − 1 (em %).
     * O mês corrente usa [livePrice] quando informado.
     */
    fun returns(closes: List<MonthClose>, livePrice: Double?, current: YearMonth): Map<YearMonth, Double> {
        val byMonth = sortedMapOf<YearMonth, Double>()
        closes.forEach { if (it.close > 0) byMonth[it.month] = it.close }
        if (livePrice != null && livePrice > 0 && byMonth.isNotEmpty() && !byMonth.lastKey().isAfter(current)) {
            byMonth[current] = livePrice
        }
        val out = LinkedHashMap<YearMonth, Double>()
        var prev: Double? = null
        for ((month, close) in byMonth) {
            val p = prev
            if (p != null && p > 0) out[month] = (close / p - 1.0) * 100.0
            prev = close
        }
        return out
    }

    /** Média simples, mês a mês, das rentabilidades de várias ações (como a variação do dia da carteira). */
    fun average(series: List<Map<YearMonth, Double>>): Map<YearMonth, Double> {
        val months = series.flatMap { it.keys }.toSortedSet()
        val out = LinkedHashMap<YearMonth, Double>()
        for (m in months) {
            val values = series.mapNotNull { it[m] }
            if (values.isNotEmpty()) out[m] = values.average()
        }
        return out
    }

    /** Rentabilidade acumulada do ano (juros compostos dos meses disponíveis), ou null sem dados. */
    fun yearTotal(returns: Map<YearMonth, Double>, year: Int): Double? {
        val months = returns.filterKeys { it.year == year }.values
        if (months.isEmpty()) return null
        return (months.fold(1.0) { acc, r -> acc * (1 + r / 100.0) } - 1.0) * 100.0
    }

    fun years(returns: Map<YearMonth, Double>): List<Int> = returns.keys.map { it.year }.distinct().sorted()

    // Serialização: "2026-01:38.1234;2026-02:39.0000"

    fun encode(closes: List<MonthClose>): String =
        closes.joinToString(";") { "${it.month}:" + String.format(Locale.US, "%.4f", it.close) }

    fun decode(text: String?): List<MonthClose> {
        if (text.isNullOrBlank()) return emptyList()
        return text.split(';').mapNotNull { part ->
            val i = part.indexOf(':')
            if (i <= 0) return@mapNotNull null
            val month = try {
                YearMonth.parse(part.substring(0, i))
            } catch (e: Exception) {
                return@mapNotNull null
            }
            val close = part.substring(i + 1).toDoubleOrNull() ?: return@mapNotNull null
            MonthClose(month, close)
        }
    }
}

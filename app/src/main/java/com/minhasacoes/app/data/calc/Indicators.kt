package com.minhasacoes.app.data.calc

import java.util.Locale

/** Tipos de média móvel. */
object MaType {
    const val SMA = "SMA"
    const val EMA = "EMA"

    fun of(value: String?): String = if (value == EMA) EMA else SMA

    /** Rótulo curto: MM20 (simples) ou MME20 (exponencial). */
    fun label(type: String?, period: Int): String = if (of(type) == EMA) "MME$period" else "MM$period"
}

/** Posição em relação à média. */
object MaState {
    const val ABOVE = "ABOVE"
    const val BELOW = "BELOW"
}

/** Resultado do cálculo para um ativo. */
data class Evaluation(
    /** Média principal (lenta) no último pregão. */
    val ma: Double?,
    /** Média rápida, apenas no modo de cruzamento de médias. */
    val fastMa: Double?,
    /** ABOVE/BELOW, ou null se não houver histórico suficiente. */
    val state: String?,
    val chartCloses: List<Double>,
    val chartMa: List<Double?>,
    val chartFast: List<Double?>?,
)

/** Cálculos puros (sem Android), cobertos por testes unitários. */
object Indicators {

    /** Quantidade de pregões exibidos no gráfico. */
    const val CHART_POINTS = 60

    /** Série da média simples alinhada aos fechamentos (null onde não há dados suficientes). */
    fun smaSeries(closes: List<Double>, period: Int): List<Double?> {
        val out = ArrayList<Double?>(closes.size)
        if (period < 1) return closes.map { null }
        var sum = 0.0
        for (i in closes.indices) {
            sum += closes[i]
            if (i >= period) sum -= closes[i - period]
            out.add(if (i >= period - 1) sum / period else null)
        }
        return out
    }

    /** Série da média exponencial, iniciada pela média simples dos primeiros [period] valores. */
    fun emaSeries(closes: List<Double>, period: Int): List<Double?> {
        val out = ArrayList<Double?>(closes.size)
        if (period < 1 || closes.size < period) return closes.map { null }
        val k = 2.0 / (period + 1)
        var ema = 0.0
        for (i in closes.indices) {
            when {
                i < period - 1 -> out.add(null)
                i == period - 1 -> {
                    ema = closes.subList(0, period).average()
                    out.add(ema)
                }
                else -> {
                    ema = closes[i] * k + ema * (1 - k)
                    out.add(ema)
                }
            }
        }
        return out
    }

    fun series(type: String?, closes: List<Double>, period: Int): List<Double?> =
        if (MaType.of(type) == MaType.EMA) emaSeries(closes, period) else smaSeries(closes, period)

    fun stateOf(value: Double?, reference: Double?): String? = when {
        value == null || reference == null -> null
        value < reference -> MaState.BELOW
        else -> MaState.ABOVE
    }

    /**
     * Avalia o ativo. Sem [fastPeriod]: preço x média. Com [fastPeriod]: média rápida x média lenta.
     * O último fechamento de [closes] deve ser o preço atual.
     */
    fun evaluate(closes: List<Double>, type: String?, period: Int, fastPeriod: Int?): Evaluation {
        val slow = series(type, closes, period)
        val fast = fastPeriod?.let { series(type, closes, it) }
        val ma = slow.lastOrNull()
        val fastMa = fast?.lastOrNull()
        val state = if (fast != null) stateOf(fastMa, ma) else stateOf(closes.lastOrNull(), ma)
        val from = (closes.size - CHART_POINTS).coerceAtLeast(0)
        return Evaluation(
            ma = ma,
            fastMa = fastMa,
            state = state,
            chartCloses = closes.subList(from, closes.size).toList(),
            chartMa = slow.subList(from, slow.size).toList(),
            chartFast = fast?.let { it.subList(from, it.size).toList() },
        )
    }

    /** Quantos pregões de histórico são necessários para a média e para o gráfico. */
    fun barsNeeded(type: String?, period: Int, fastPeriod: Int? = null): Int {
        val p = maxOf(period, fastPeriod ?: 0)
        val base = p + CHART_POINTS
        return if (MaType.of(type) == MaType.EMA) maxOf(base, p * 3) else base
    }

    /** Intervalo do Yahoo Finance que cobre [bars] pregões. */
    fun rangeFor(bars: Int): String = when {
        bars <= 120 -> "6mo"
        bars <= 245 -> "1y"
        bars <= 495 -> "2y"
        bars <= 1240 -> "5y"
        else -> "10y"
    }

    enum class Cross { WENT_BELOW, WENT_ABOVE }

    /** Detecta cruzamento entre o estado anterior e o novo. O primeiro cálculo nunca gera alerta. */
    fun crossOf(oldState: String?, newState: String?): Cross? = when {
        oldState == null || newState == null || oldState == newState -> null
        newState == MaState.BELOW -> Cross.WENT_BELOW
        else -> Cross.WENT_ABOVE
    }

    /** Decide se notifica: queda sempre (com alerta ligado); alta só se o usuário pediu. */
    fun shouldNotify(cross: Cross?, alertEnabled: Boolean, notifyAbove: Boolean): Boolean = when (cross) {
        null -> false
        Cross.WENT_BELOW -> alertEnabled
        Cross.WENT_ABOVE -> alertEnabled && notifyAbove
    }

    // Serialização compacta das séries do gráfico.

    fun encode(values: List<Double?>): String =
        values.joinToString(",") { v -> if (v == null || v.isNaN()) "" else String.format(Locale.US, "%.4f", v) }

    fun decode(text: String?): List<Double?> {
        if (text.isNullOrEmpty()) return emptyList()
        return text.split(',').map { it.toDoubleOrNull() }
    }

    /** "petr4" -> "PETR4.SA"; símbolos estrangeiros ou já completos ficam como digitados. */
    fun normalizeSymbol(input: String): String {
        val s = input.trim().uppercase().replace(" ", "")
        val b3 = Regex("^([A-Z]{4}\\d{1,2})F?$").matchEntire(s)
        return if (b3 != null) "${b3.groupValues[1]}.SA" else s
    }
}

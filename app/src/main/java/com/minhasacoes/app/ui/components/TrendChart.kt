package com.minhasacoes.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Gráfico de linha do preço com a(s) média(s). [ma] e [fast] são alinhadas a [closes]
 * (valores null onde a média ainda não existe).
 */
@Composable
fun TrendChart(
    closes: List<Double?>,
    ma: List<Double?>,
    fast: List<Double?>?,
    priceColor: Color,
    maColor: Color,
    fastColor: Color,
    modifier: Modifier = Modifier,
    priceWidth: Dp = 2.dp,
    maWidth: Dp = 1.5.dp,
    fill: Boolean = false,
    showLastDot: Boolean = false,
) {
    Canvas(modifier) {
        val n = closes.size
        if (n < 2) return@Canvas
        val values = buildList {
            closes.forEach { if (it != null) add(it) }
            ma.forEach { if (it != null) add(it) }
            fast?.forEach { if (it != null) add(it) }
        }
        if (values.isEmpty()) return@Canvas
        val min = values.min()
        val max = values.max()
        val span = if (max - min > 0) max - min else 1.0
        val pad = priceWidth.toPx() * 2
        val h = size.height - pad * 2
        val stepX = size.width / (n - 1)

        fun x(i: Int) = i * stepX
        fun y(v: Double) = (pad + h - ((v - min) / span * h)).toFloat()

        // Média principal (tracejada) e média rápida.
        drawSeries(ma, ::x, ::y, maColor, maWidth.toPx(), dashed = true)
        if (fast != null) drawSeries(fast, ::x, ::y, fastColor, maWidth.toPx(), dashed = false)

        // Preço.
        val price = Path()
        var started = false
        var firstX = 0f
        var lastX = 0f
        var lastY = 0f
        closes.forEachIndexed { i, v ->
            if (v == null) return@forEachIndexed
            val px = x(i)
            val py = y(v)
            if (!started) {
                price.moveTo(px, py)
                firstX = px
                started = true
            } else {
                price.lineTo(px, py)
            }
            lastX = px
            lastY = py
        }
        if (!started) return@Canvas

        if (fill) {
            val area = Path().apply {
                addPath(price)
                lineTo(lastX, size.height)
                lineTo(firstX, size.height)
                close()
            }
            drawPath(
                area,
                Brush.verticalGradient(listOf(priceColor.copy(alpha = 0.22f), priceColor.copy(alpha = 0f))),
            )
        }
        drawPath(
            price,
            priceColor,
            style = Stroke(width = priceWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        if (showLastDot) {
            drawCircle(priceColor, radius = priceWidth.toPx() * 2.2f, center = Offset(lastX, lastY))
        }
    }
}

private fun DrawScope.drawSeries(
    series: List<Double?>,
    x: (Int) -> Float,
    y: (Double) -> Float,
    color: Color,
    width: Float,
    dashed: Boolean,
) {
    val path = Path()
    var drawing = false
    series.forEachIndexed { i, v ->
        if (v == null) {
            drawing = false
        } else if (!drawing) {
            path.moveTo(x(i), y(v))
            drawing = true
        } else {
            path.lineTo(x(i), y(v))
        }
    }
    drawPath(
        path,
        color,
        style = Stroke(
            width = width,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(width * 4, width * 3)) else null,
        ),
    )
}

package com.minhasacoes.app

import com.minhasacoes.app.data.calc.Indicators
import com.minhasacoes.app.data.calc.MaState
import com.minhasacoes.app.data.calc.MaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicatorsTest {

    private val eps = 1e-9

    @Test
    fun smaSeries_matchesManualAverage() {
        val s = Indicators.smaSeries(listOf(1.0, 2.0, 3.0, 4.0, 5.0), 3)
        assertNull(s[0])
        assertNull(s[1])
        assertEquals(2.0, s[2]!!, eps)
        assertEquals(3.0, s[3]!!, eps)
        assertEquals(4.0, s[4]!!, eps)
    }

    @Test
    fun smaSeries_insufficientHistory_isAllNull() {
        assertTrue(Indicators.smaSeries(listOf(1.0, 2.0), 5).all { it == null })
    }

    @Test
    fun emaSeries_seedsWithSmaThenSmooths() {
        val s = Indicators.emaSeries(listOf(1.0, 2.0, 3.0, 4.0, 5.0), 3)
        assertNull(s[1])
        assertEquals(2.0, s[2]!!, eps) // SMA inicial
        // k = 2/(3+1) = 0.5 -> 4*0.5 + 2*0.5 = 3 ; 5*0.5 + 3*0.5 = 4
        assertEquals(3.0, s[3]!!, eps)
        assertEquals(4.0, s[4]!!, eps)
    }

    @Test
    fun emaReactsFasterThanSma() {
        val closes = List(30) { 10.0 } + List(5) { 20.0 }
        val sma = Indicators.smaSeries(closes, 10).last()!!
        val ema = Indicators.emaSeries(closes, 10).last()!!
        assertTrue(ema > sma)
    }

    @Test
    fun evaluate_priceAboveAndBelowAverage() {
        val up = Indicators.evaluate(listOf(10.0, 10.0, 10.0, 13.0), MaType.SMA, 3, null)
        assertEquals(MaState.ABOVE, up.state)
        assertEquals(11.0, up.ma!!, eps)

        val down = Indicators.evaluate(listOf(10.0, 10.0, 10.0, 7.0), MaType.SMA, 3, null)
        assertEquals(MaState.BELOW, down.state)
    }

    @Test
    fun evaluate_withoutEnoughHistory_hasNoState() {
        val e = Indicators.evaluate(listOf(10.0, 11.0), MaType.SMA, 20, null)
        assertNull(e.ma)
        assertNull(e.state)
    }

    @Test
    fun evaluate_crossModeComparesFastWithSlow() {
        // Queda recente: a média rápida (2) fica abaixo da lenta (4) mesmo com preço acima de mínimas.
        val closes = listOf(10.0, 10.0, 10.0, 10.0, 8.0, 8.0)
        val e = Indicators.evaluate(closes, MaType.SMA, 4, 2)
        assertEquals(8.0, e.fastMa!!, eps)
        assertEquals(9.0, e.ma!!, eps)
        assertEquals(MaState.BELOW, e.state)
        assertEquals(closes.size, e.chartFast!!.size)
    }

    @Test
    fun evaluate_chartKeepsLastPointsAligned() {
        val closes = List(100) { it + 1.0 }
        val e = Indicators.evaluate(closes, MaType.SMA, 20, null)
        assertEquals(Indicators.CHART_POINTS, e.chartCloses.size)
        assertEquals(Indicators.CHART_POINTS, e.chartMa.size)
        assertEquals(100.0, e.chartCloses.last(), eps)
        assertEquals(e.ma!!, e.chartMa.last()!!, eps)
    }

    @Test
    fun crossOf_detectsOnlyRealChanges() {
        assertNull(Indicators.crossOf(null, MaState.BELOW))
        assertNull(Indicators.crossOf(MaState.ABOVE, MaState.ABOVE))
        assertNull(Indicators.crossOf(MaState.ABOVE, null))
        assertEquals(Indicators.Cross.WENT_BELOW, Indicators.crossOf(MaState.ABOVE, MaState.BELOW))
        assertEquals(Indicators.Cross.WENT_ABOVE, Indicators.crossOf(MaState.BELOW, MaState.ABOVE))
    }

    @Test
    fun shouldNotify_respectsSettings() {
        assertTrue(Indicators.shouldNotify(Indicators.Cross.WENT_BELOW, alertEnabled = true, notifyAbove = false))
        assertFalse(Indicators.shouldNotify(Indicators.Cross.WENT_BELOW, alertEnabled = false, notifyAbove = true))
        assertFalse(Indicators.shouldNotify(Indicators.Cross.WENT_ABOVE, alertEnabled = true, notifyAbove = false))
        assertTrue(Indicators.shouldNotify(Indicators.Cross.WENT_ABOVE, alertEnabled = true, notifyAbove = true))
        assertFalse(Indicators.shouldNotify(null, alertEnabled = true, notifyAbove = true))
    }

    @Test
    fun encodeDecode_roundTripWithGaps() {
        val values = listOf(null, 1.5, 2.25, null, 3.0)
        val decoded = Indicators.decode(Indicators.encode(values))
        assertEquals(values.size, decoded.size)
        assertNull(decoded[0])
        assertEquals(2.25, decoded[2]!!, 1e-4)
        assertNull(decoded[3])
        assertTrue(Indicators.decode(null).isEmpty())
    }

    @Test
    fun normalizeSymbol_handlesB3AndForeign() {
        assertEquals("PETR4.SA", Indicators.normalizeSymbol(" petr4 "))
        assertEquals("BOVA11.SA", Indicators.normalizeSymbol("BOVA11"))
        assertEquals("PETR4.SA", Indicators.normalizeSymbol("PETR4F"))
        assertEquals("AAPL", Indicators.normalizeSymbol("aapl"))
        assertEquals("VALE3.SA", Indicators.normalizeSymbol("VALE3.SA"))
    }

    @Test
    fun rangeAndBars_coverRequestedPeriod() {
        assertEquals("6mo", Indicators.rangeFor(Indicators.barsNeeded(MaType.SMA, 20)))
        assertEquals("2y", Indicators.rangeFor(Indicators.barsNeeded(MaType.SMA, 200)))
        assertTrue(Indicators.barsNeeded(MaType.EMA, 50) >= 150)
    }

    @Test
    fun maTypeLabels() {
        assertEquals("MM20", MaType.label(null, 20))
        assertEquals("MME9", MaType.label(MaType.EMA, 9))
    }
}

package com.minhasacoes.app

import com.minhasacoes.app.data.api.YahooParser
import com.minhasacoes.app.data.calc.MonthClose
import com.minhasacoes.app.data.calc.Monthly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class MonthlyTest {

    private val eps = 1e-9
    private fun ym(y: Int, m: Int) = YearMonth.of(y, m)

    @Test
    fun returns_monthOverMonth_withLivePriceForCurrentMonth() {
        val closes = listOf(
            MonthClose(ym(2026, 1), 100.0),
            MonthClose(ym(2026, 2), 110.0),
            MonthClose(ym(2026, 3), 99.0),
        )
        val r = Monthly.returns(closes, livePrice = 104.94, current = ym(2026, 3))
        assertFalse(r.containsKey(ym(2026, 1))) // primeiro mês não tem base
        assertEquals(10.0, r[ym(2026, 2)]!!, eps)
        assertEquals(-4.6, r[ym(2026, 3)]!!, 1e-6) // 104,94 / 110 − 1
    }

    @Test
    fun returns_appendsCurrentMonthWhenNewMonthStarted() {
        val closes = listOf(MonthClose(ym(2026, 1), 50.0))
        val r = Monthly.returns(closes, livePrice = 55.0, current = ym(2026, 2))
        assertEquals(10.0, r[ym(2026, 2)]!!, eps)
    }

    @Test
    fun average_isPerMonthAcrossAssets() {
        val a = mapOf(ym(2026, 1) to 10.0, ym(2026, 2) to -2.0)
        val b = mapOf(ym(2026, 1) to 0.0)
        val avg = Monthly.average(listOf(a, b))
        assertEquals(5.0, avg[ym(2026, 1)]!!, eps)
        assertEquals(-2.0, avg[ym(2026, 2)]!!, eps)
    }

    @Test
    fun yearTotal_compoundsMonths() {
        val r = mapOf(ym(2026, 1) to 10.0, ym(2026, 2) to 10.0, ym(2025, 12) to 50.0)
        assertEquals(21.0, Monthly.yearTotal(r, 2026)!!, 1e-9)
        assertNull(Monthly.yearTotal(r, 2024))
        assertEquals(listOf(2025, 2026), Monthly.years(r))
    }

    @Test
    fun encodeDecode_roundTrip_ignoresGarbage() {
        val closes = listOf(MonthClose(ym(2025, 11), 12.5), MonthClose(ym(2025, 12), 13.25))
        val back = Monthly.decode(Monthly.encode(closes) + ";lixo;2026-13:1")
        assertEquals(2, back.size)
        assertEquals(ym(2025, 12), back[1].month)
        assertEquals(13.25, back[1].close, 1e-4)
        assertTrue(Monthly.decode(null).isEmpty())
    }

    @Test
    fun yahooMonthly_keepsLastValuePerMonth() {
        // 1º/out e 1º/nov de 2026 (03h UTC = 00h em SP) e um candle repetido de novembro (dia 6).
        val body = """
            {"chart":{"result":[{
              "meta":{"gmtoffset":-10800},
              "timestamp":[1790823600,1793502000,1793934000],
              "indicators":{"quote":[{"close":[30.0,31.0,32.0]}]}
            }],"error":null}}
        """.trimIndent()
        val list = YahooParser.parseMonthly(body)
        assertEquals(2, list.size)
        assertEquals(ym(2026, 10), list[0].month)
        assertEquals(ym(2026, 11), list[1].month)
        assertEquals(32.0, list[1].close, eps)
    }
}

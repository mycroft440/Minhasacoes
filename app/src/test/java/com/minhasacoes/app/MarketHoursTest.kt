package com.minhasacoes.app

import com.minhasacoes.app.data.calc.MarketHours
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class MarketHoursTest {

    private val sp = ZoneId.of("America/Sao_Paulo")

    private fun at(text: String) = LocalDateTime.parse(text).atZone(sp).toInstant()

    @Test
    fun b3_openDuringSession_closedAtNightAndWeekend() {
        assertTrue(MarketHours.isOpen(MarketHours.B3, at("2026-11-03T11:00")))  // terça
        assertFalse(MarketHours.isOpen(MarketHours.B3, at("2026-11-03T21:00")))
        assertFalse(MarketHours.isOpen(MarketHours.B3, at("2026-11-07T11:00"))) // sábado
    }

    @Test
    fun refreshesDuringSession() {
        val now = at("2026-11-03T14:00")
        assertTrue(MarketHours.shouldAutoRefresh(now, now.toEpochMilli() - 3_600_000, hasB3 = true, hasOther = false))
    }

    @Test
    fun refreshesOnceAfterClose_thenStops() {
        val night = at("2026-11-03T20:00")
        val beforeClose = at("2026-11-03T17:30").toEpochMilli()
        val afterClose = at("2026-11-03T18:45").toEpochMilli()
        assertTrue(MarketHours.shouldAutoRefresh(night, beforeClose, hasB3 = true, hasOther = false))
        assertFalse(MarketHours.shouldAutoRefresh(night, afterClose, hasB3 = true, hasOther = false))
    }

    @Test
    fun weekend_usesFridayClose() {
        val sunday = at("2026-11-08T12:00")
        val fridayNight = at("2026-11-06T19:00").toEpochMilli()
        val fridayAfternoon = at("2026-11-06T15:00").toEpochMilli()
        assertFalse(MarketHours.shouldAutoRefresh(sunday, fridayNight, hasB3 = true, hasOther = false))
        assertTrue(MarketHours.shouldAutoRefresh(sunday, fridayAfternoon, hasB3 = true, hasOther = false))
    }

    @Test
    fun noAssets_neverRefreshes() {
        assertFalse(MarketHours.shouldAutoRefresh(at("2026-11-03T14:00"), 0L, hasB3 = false, hasOther = false))
    }

    @Test
    fun usSession_keepsRefreshingAfterB3Close() {
        // 17h30 em SP = 15h30 em Nova York (sem horário de verão nos EUA em novembro após dia 1).
        val now = at("2026-11-03T17:15")
        assertTrue(MarketHours.isOpen(MarketHours.US, now))
    }
}

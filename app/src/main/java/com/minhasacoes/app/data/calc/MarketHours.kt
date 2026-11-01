package com.minhasacoes.app.data.calc

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Janelas de pregão usadas pela atualização automática. Fora delas os preços não mudam,
 * então o app só busca uma vez depois do fechamento para registrar o último preço.
 * Feriados não são considerados (no máximo geram uma atualização sem mudança).
 */
object MarketHours {

    data class Session(val zone: ZoneId, val open: LocalTime, val close: LocalTime)

    /** B3: pregão 10h–17h/18h (varia com o horário de verão dos EUA); margem incluída. */
    val B3 = Session(ZoneId.of("America/Sao_Paulo"), LocalTime.of(9, 55), LocalTime.of(18, 30))

    /** NYSE/Nasdaq: 9h30–16h (Nova York); margem incluída. */
    val US = Session(ZoneId.of("America/New_York"), LocalTime.of(9, 25), LocalTime.of(16, 30))

    private fun isWeekday(date: LocalDate): Boolean =
        date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY

    fun isOpen(session: Session, now: Instant): Boolean {
        val t = now.atZone(session.zone)
        val time = t.toLocalTime()
        return isWeekday(t.toLocalDate()) && !time.isBefore(session.open) && !time.isAfter(session.close)
    }

    /** Último fechamento (fim da janela) que já aconteceu antes de [now]. */
    fun lastClose(session: Session, now: Instant): Instant {
        var date = now.atZone(session.zone).toLocalDate()
        repeat(8) {
            if (isWeekday(date)) {
                val close = date.atTime(session.close).atZone(session.zone).toInstant()
                if (!close.isAfter(now)) return close
            }
            date = date.minusDays(1)
        }
        return now.minusSeconds(7 * 86_400L)
    }

    fun sessionsFor(hasB3: Boolean, hasOther: Boolean): List<Session> = buildList {
        if (hasB3) add(B3)
        if (hasOther) add(US)
    }

    /**
     * Atualiza se algum mercado relevante estiver aberto, ou se ainda não houve
     * atualização depois do último fechamento.
     */
    fun shouldAutoRefresh(now: Instant, lastUpdateMillis: Long, hasB3: Boolean, hasOther: Boolean): Boolean {
        val sessions = sessionsFor(hasB3, hasOther)
        if (sessions.isEmpty()) return false
        if (sessions.any { isOpen(it, now) }) return true
        val latestClose = sessions.maxOf { lastClose(it, now) }
        return lastUpdateMillis < latestClose.toEpochMilli()
    }
}

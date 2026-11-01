package com.minhasacoes.app

import com.minhasacoes.app.data.api.BrapiParser
import com.minhasacoes.app.data.api.SymbolNotFoundException
import com.minhasacoes.app.data.api.YahooParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {

    private val eps = 1e-9

    @Test
    fun yahoo_replacesTodaysCandleWithLivePrice_andSkipsNulls() {
        // 3 pregões diários (00h UTC de cada dia, offset -3h), o 2º com close nulo.
        val body = """
            {"chart":{"result":[{
              "meta":{"currency":"BRL","shortName":"PETROBRAS PN","regularMarketPrice":38.5,
                      "regularMarketTime":1762354800,"gmtoffset":-10800},
              "timestamp":[1762171200,1762257600,1762344000],
              "indicators":{"quote":[{"close":[36.0,null,38.0]}]}
            }],"error":null}}
        """.trimIndent()
        val q = YahooParser.parseChart("PETR4.SA", body)
        assertEquals(listOf(36.0, 38.5), q.closes)
        assertEquals(36.0, q.prevClose!!, eps)
        assertEquals(38.5, q.price, eps)
        assertEquals("BRL", q.currency)
        assertEquals("PETROBRAS PN", q.name)
    }

    @Test
    fun yahoo_appendsPriceWhenTodayIsMissing() {
        val body = """
            {"chart":{"result":[{
              "meta":{"currency":"USD","regularMarketPrice":101.0,"regularMarketTime":1762444800,"gmtoffset":-18000},
              "timestamp":[1762257600,1762344000],
              "indicators":{"quote":[{"close":[99.0,100.0]}]}
            }],"error":null}}
        """.trimIndent()
        val q = YahooParser.parseChart("AAPL", body)
        assertEquals(listOf(99.0, 100.0, 101.0), q.closes)
        assertEquals(100.0, q.prevClose!!, eps)
    }

    @Test(expected = SymbolNotFoundException::class)
    fun yahoo_emptyResultIsNotFound() {
        YahooParser.parseChart("XXXX", """{"chart":{"result":null,"error":{"code":"Not Found"}}}""")
    }

    @Test
    fun yahoo_searchPutsB3First_andFiltersTypes() {
        val body = """
            {"quotes":[
              {"symbol":"PBR","shortname":"Petroleo Brasileiro","exchDisp":"NYSE","quoteType":"EQUITY"},
              {"symbol":"PETR4.SA","shortname":"PETROBRAS PN","exchDisp":"São Paulo","quoteType":"EQUITY"},
              {"symbol":"BRL=X","shortname":"USD/BRL","quoteType":"CURRENCY"}
            ]}
        """.trimIndent()
        val list = YahooParser.parseSearch(body)
        assertEquals(2, list.size)
        assertEquals("PETR4.SA", list[0].symbol)
        assertEquals("São Paulo", list[0].exchange)
    }

    @Test
    fun yahoo_searchToleratesGarbage() {
        assertTrue(YahooParser.parseSearch("not json").isEmpty())
    }

    @Test
    fun brapi_appendsPriceWhenHistoryEndsAtPreviousClose() {
        val body = """
            {"results":[{"symbol":"VALE3","shortName":"VALE ON","currency":"BRL",
              "regularMarketPrice":62.0,"regularMarketPreviousClose":60.0,
              "historicalDataPrice":[{"date":2,"close":60.0},{"date":1,"close":59.0}]}]}
        """.trimIndent()
        val q = BrapiParser.parse("VALE3.SA", body)
        assertEquals(listOf(59.0, 60.0, 62.0), q.closes)
        assertEquals(60.0, q.prevClose!!, eps)
        assertEquals("VALE3.SA", q.symbol)
    }

    @Test
    fun brapi_replacesTodayWhenAlreadyInHistory() {
        val body = """
            {"results":[{"symbol":"ITUB4","regularMarketPrice":33.2,"regularMarketPreviousClose":32.0,
              "historicalDataPrice":[{"date":1,"close":32.0},{"date":2,"close":33.0}]}]}
        """.trimIndent()
        val q = BrapiParser.parse("ITUB4.SA", body)
        assertEquals(listOf(32.0, 33.2), q.closes)
        assertEquals("BRL", q.currency)
    }
}

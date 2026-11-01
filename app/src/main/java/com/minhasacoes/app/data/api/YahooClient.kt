package com.minhasacoes.app.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Cliente gratuito do Yahoo Finance (endpoints públicos, sem chave de API).
 * Cobre ações da B3 (sufixo .SA), EUA, ETFs, FIIs e índices.
 */
class YahooClient(private val http: OkHttpClient) {

    suspend fun fetch(symbol: String, range: String): Quote = withContext(Dispatchers.IO) {
        val path = "/v8/finance/chart/${enc(symbol)}?range=$range&interval=1d&includePrePost=false"
        YahooParser.parseChart(symbol, get(path))
    }

    suspend fun search(query: String): List<SymbolSuggestion> = withContext(Dispatchers.IO) {
        val path = "/v1/finance/search?q=${enc(query)}&quotesCount=8&newsCount=0&listsCount=0&lang=pt-BR&region=BR"
        YahooParser.parseSearch(get(path))
    }

    /** Tenta os dois servidores do Yahoo; "não encontrado" não é repetido. */
    private fun get(path: String): String {
        var lastError: IOException? = null
        for (host in HOSTS) {
            try {
                val request = Request.Builder()
                    .url("https://$host$path")
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
                http.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.code == 404) throw SymbolNotFoundException()
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    return body
                }
            } catch (e: SymbolNotFoundException) {
                throw e
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Falha ao acessar o Yahoo Finance")
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        private val HOSTS = listOf("query1.finance.yahoo.com", "query2.finance.yahoo.com")
    }
}

/** Interpretação das respostas do Yahoo (separada para testes). */
object YahooParser {

    fun parseChart(symbol: String, body: String): Quote {
        try {
            val chart = JSONObject(body).getJSONObject("chart")
            val result = chart.optJSONArray("result")?.optJSONObject(0)
                ?: throw SymbolNotFoundException()
            val meta = result.getJSONObject("meta")

            val timestamps = result.optJSONArray("timestamp")
            val closeArray = result.optJSONObject("indicators")
                ?.optJSONArray("quote")
                ?.optJSONObject(0)
                ?.optJSONArray("close")

            val times = ArrayList<Long>()
            val closes = ArrayList<Double>()
            if (timestamps != null && closeArray != null) {
                val n = minOf(timestamps.length(), closeArray.length())
                for (i in 0 until n) {
                    if (closeArray.isNull(i)) continue
                    val c = closeArray.optDouble(i)
                    if (c.isNaN() || c <= 0.0) continue
                    times.add(timestamps.optLong(i))
                    closes.add(c)
                }
            }

            val marketPrice = meta.optDouble("regularMarketPrice")
            val price = when {
                !marketPrice.isNaN() && marketPrice > 0 -> marketPrice
                closes.isNotEmpty() -> closes.last()
                else -> throw SymbolNotFoundException()
            }

            // Garante que o último fechamento da série é o preço atual.
            val gmtOffset = meta.optLong("gmtoffset", 0L)
            val marketTime = meta.optLong("regularMarketTime", 0L)
            val prevClose: Double?
            if (closes.isEmpty()) {
                closes.add(price)
                prevClose = meta.optDouble("chartPreviousClose").takeIf { !it.isNaN() && it > 0 }
            } else {
                val lastDay = Math.floorDiv(times.last() + gmtOffset, 86_400L)
                val marketDay = Math.floorDiv(marketTime + gmtOffset, 86_400L)
                if (marketTime == 0L || marketDay <= lastDay) {
                    closes[closes.lastIndex] = price
                    prevClose = closes.getOrNull(closes.lastIndex - 1)
                } else {
                    prevClose = closes.last()
                    closes.add(price)
                }
            }

            val name = meta.optString("shortName").ifBlank { meta.optString("longName") }.ifBlank { null }
            val currency = meta.optString("currency").ifBlank { null }

            return Quote(
                symbol = symbol,
                name = name?.trim(),
                currency = currency,
                price = price,
                prevClose = prevClose,
                closes = closes,
            )
        } catch (e: JSONException) {
            throw IOException("Resposta inválida", e)
        }
    }

    private val ALLOWED_TYPES = setOf("EQUITY", "ETF", "INDEX", "MUTUALFUND")

    /** Sugestões da busca, com ativos da B3 primeiro. */
    fun parseSearch(body: String): List<SymbolSuggestion> {
        val quotes = try {
            JSONObject(body).optJSONArray("quotes")
        } catch (e: JSONException) {
            null
        } ?: return emptyList()

        val out = ArrayList<SymbolSuggestion>()
        for (i in 0 until quotes.length()) {
            val q = quotes.optJSONObject(i) ?: continue
            val symbol = q.optString("symbol")
            if (symbol.isBlank()) continue
            val type = q.optString("quoteType")
            if (type.isNotBlank() && type !in ALLOWED_TYPES) continue
            val name = q.optString("shortname").ifBlank { q.optString("longname") }.ifBlank { symbol }
            val exchange = q.optString("exchDisp").ifBlank { q.optString("exchange") }.ifBlank { null }
            out.add(SymbolSuggestion(symbol = symbol, name = name.trim(), exchange = exchange))
        }
        return out.sortedBy { if (it.symbol.endsWith(".SA")) 0 else 1 }
    }
}

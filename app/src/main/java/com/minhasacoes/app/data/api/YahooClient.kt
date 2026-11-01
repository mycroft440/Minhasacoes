package com.minhasacoes.app.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class SymbolNotFoundException : IOException("Ativo não encontrado")

/**
 * Cotação de um ativo com o histórico de fechamentos diários (o último fechamento
 * já é o preço atual quando o pregão está aberto).
 */
data class Quote(
    val symbol: String,
    val name: String?,
    val currency: String?,
    val price: Double,
    val prevClose: Double?,
    val closes: List<Double>,
) {
    /** Média móvel simples dos últimos [period] fechamentos, ou null se não houver histórico suficiente. */
    fun sma(period: Int): Double? =
        if (period < 1 || closes.size < period) null else closes.takeLast(period).average()
}

/**
 * Cliente gratuito do Yahoo Finance (endpoint público v8/chart, sem chave de API).
 * Cobre ações da B3 (sufixo .SA), EUA, ETFs, FIIs e índices.
 */
class YahooClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {

    suspend fun fetch(symbol: String, range: String): Quote = withContext(Dispatchers.IO) {
        var lastError: IOException? = null
        for (host in HOSTS) {
            try {
                return@withContext request(host, symbol, range)
            } catch (e: SymbolNotFoundException) {
                throw e
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Falha ao buscar cotação")
    }

    private fun request(host: String, symbol: String, range: String): Quote {
        val encoded = URLEncoder.encode(symbol, "UTF-8")
        val url = "https://$host/v8/finance/chart/$encoded?range=$range&interval=1d&includePrePost=false"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()

        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code == 404) throw SymbolNotFoundException()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return parse(symbol, body)
        }
    }

    private fun parse(symbol: String, body: String): Quote {
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

    companion object {
        private val HOSTS = listOf("query1.finance.yahoo.com", "query2.finance.yahoo.com")
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

        /** Intervalo de histórico suficiente para calcular a média do período. */
        fun rangeFor(period: Int): String = when {
            period <= 100 -> "6mo"
            period <= 200 -> "1y"
            period <= 450 -> "2y"
            else -> "5y"
        }

        /** "petr4" -> "PETR4.SA"; símbolos estrangeiros ficam como digitados. */
        fun normalize(input: String): String {
            val s = input.trim().uppercase().replace(" ", "")
            val b3 = Regex("^([A-Z]{4}\\d{1,2})F?$").matchEntire(s)
            return if (b3 != null) "${b3.groupValues[1]}.SA" else s
        }
    }
}

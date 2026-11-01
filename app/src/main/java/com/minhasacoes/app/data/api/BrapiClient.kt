package com.minhasacoes.app.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import kotlin.math.abs

/**
 * Fonte reserva para ações da B3 (brapi.dev), usada quando o Yahoo falha.
 * Sem token só funcionam alguns tickers de teste; com o token gratuito, qualquer ação
 * com até 3 meses de histórico (suficiente para médias de até ~60 pregões).
 */
class BrapiClient(private val http: OkHttpClient) {

    suspend fun fetch(symbol: String, token: String?): Quote = withContext(Dispatchers.IO) {
        val ticker = symbol.removeSuffix(".SA")
        val url = "https://brapi.dev/api/quote/${URLEncoder.encode(ticker, "UTF-8")}?range=3mo&interval=1d"
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept", "application/json")
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer ${token.trim()}")

        http.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code == 404) throw SymbolNotFoundException()
            if (!response.isSuccessful) throw IOException("brapi HTTP ${response.code}")
            BrapiParser.parse(symbol, body)
        }
    }
}

object BrapiParser {

    fun parse(symbol: String, body: String): Quote {
        try {
            val result = JSONObject(body).optJSONArray("results")?.optJSONObject(0)
                ?: throw SymbolNotFoundException()

            val hist = result.optJSONArray("historicalDataPrice")
            val points = ArrayList<Pair<Long, Double>>()
            if (hist != null) {
                for (i in 0 until hist.length()) {
                    val item = hist.optJSONObject(i) ?: continue
                    if (item.isNull("close")) continue
                    val close = item.optDouble("close")
                    if (close.isNaN() || close <= 0) continue
                    points.add(item.optLong("date") to close)
                }
            }
            points.sortBy { it.first }
            val closes = points.map { it.second }.toMutableList()

            val marketPrice = result.optDouble("regularMarketPrice")
            val price = when {
                !marketPrice.isNaN() && marketPrice > 0 -> marketPrice
                closes.isNotEmpty() -> closes.last()
                else -> throw SymbolNotFoundException()
            }
            val prev = result.optDouble("regularMarketPreviousClose").takeIf { !it.isNaN() && it > 0 }

            // Se o último fechamento do histórico é o fechamento anterior, o dia de hoje ainda não está na série.
            if (closes.isEmpty()) {
                closes.add(price)
            } else if (prev != null && abs(closes.last() - prev) / prev < 1e-6 && abs(price - prev) > 1e-9) {
                closes.add(price)
            } else {
                closes[closes.lastIndex] = price
            }

            val name = result.optString("shortName").ifBlank { result.optString("longName") }.ifBlank { null }
            val currency = result.optString("currency").ifBlank { "BRL" }

            return Quote(
                symbol = symbol,
                name = name?.trim(),
                currency = currency,
                price = price,
                prevClose = prev ?: closes.getOrNull(closes.lastIndex - 1),
                closes = closes,
            )
        } catch (e: JSONException) {
            throw IOException("Resposta inválida", e)
        }
    }
}

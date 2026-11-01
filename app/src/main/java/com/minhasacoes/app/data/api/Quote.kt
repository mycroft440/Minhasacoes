package com.minhasacoes.app.data.api

import java.io.IOException

class SymbolNotFoundException : IOException("Ativo não encontrado")

/**
 * Cotação com o histórico de fechamentos diários. O último item de [closes]
 * é sempre o preço atual (durante o pregão) ou o último fechamento.
 */
data class Quote(
    val symbol: String,
    val name: String?,
    val currency: String?,
    val price: Double,
    val prevClose: Double?,
    val closes: List<Double>,
)

/** Sugestão da busca de ativos. */
data class SymbolSuggestion(
    val symbol: String,
    val name: String,
    val exchange: String?,
)

const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

package com.minhasacoes.app.data

import android.content.Context
import androidx.room.withTransaction
import com.minhasacoes.app.data.api.BrapiClient
import com.minhasacoes.app.data.api.Quote
import com.minhasacoes.app.data.api.SymbolNotFoundException
import com.minhasacoes.app.data.api.SymbolSuggestion
import com.minhasacoes.app.data.api.YahooClient
import com.minhasacoes.app.data.calc.Indicators
import com.minhasacoes.app.data.calc.MaType
import com.minhasacoes.app.data.calc.MarketHours
import com.minhasacoes.app.data.db.AppDatabase
import com.minhasacoes.app.data.db.AssetEntity
import com.minhasacoes.app.data.db.WalletEntity
import com.minhasacoes.app.data.db.WalletWithAssets
import com.minhasacoes.app.notify.Notifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Configuração escolhida para a média de uma ação. */
data class MaConfig(
    val type: String,
    val period: Int,
    val fastPeriod: Int?,
)

class Repository(
    private val context: Context,
    private val db: AppDatabase,
) {
    private val dao = db.dao()
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val yahoo = YahooClient(http)
    private val brapi = BrapiClient(http)

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _lastUpdate = MutableStateFlow(prefs.getLong(KEY_LAST_UPDATE, 0L))
    val lastUpdate: StateFlow<Long> = _lastUpdate.asStateFlow()

    private val _notifyAbove = MutableStateFlow(prefs.getBoolean(KEY_NOTIFY_ABOVE, false))
    val notifyAbove: StateFlow<Boolean> = _notifyAbove.asStateFlow()

    private val _brapiToken = MutableStateFlow(prefs.getString(KEY_BRAPI_TOKEN, "").orEmpty())
    val brapiToken: StateFlow<String> = _brapiToken.asStateFlow()

    fun setNotifyAbove(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_ABOVE, enabled).apply()
        _notifyAbove.value = enabled
    }

    fun setBrapiToken(token: String) {
        val clean = token.trim()
        prefs.edit().putString(KEY_BRAPI_TOKEN, clean).apply()
        _brapiToken.value = clean
    }

    fun observeWallets(): Flow<List<WalletWithAssets>> = dao.observeWallets()

    fun observeWallet(id: Long): Flow<WalletWithAssets?> = dao.observeWallet(id)

    suspend fun walletsSnapshot(): List<WalletWithAssets> = dao.walletsOnce()

    // ---------- Carteiras ----------

    suspend fun createWallet(name: String, period: Int, maType: String): Long =
        dao.insertWallet(WalletEntity(name = name.trim(), defaultPeriod = period, defaultMaType = MaType.of(maType)))

    suspend fun renameWallet(id: Long, name: String) = dao.renameWallet(id, name.trim())

    /** Altera a média padrão e aplica a todas as ações da carteira. */
    suspend fun setWalletDefaults(id: Long, period: Int, maType: String) {
        val type = MaType.of(maType)
        db.withTransaction {
            dao.setWalletDefaults(id, period, type)
            dao.applyDefaultsToWallet(id, period, type)
        }
        refreshSymbols(dao.assetsOfWallet(id).map { it.symbol }.toSet())
    }

    suspend fun deleteWallet(id: Long) {
        db.withTransaction {
            dao.deleteAssetsOfWallet(id)
            dao.deleteWallet(id)
        }
    }

    // ---------- Ativos ----------

    suspend fun search(query: String): List<SymbolSuggestion> = yahoo.search(query.trim())

    /** Valida o ticker buscando a cotação e adiciona à carteira. Lança exceção com mensagem amigável. */
    suspend fun addAsset(walletId: Long, input: String, config: MaConfig) {
        val symbol = Indicators.normalizeSymbol(input)
        if (symbol.isBlank()) throw IllegalArgumentException("Digite um ticker")
        if (dao.countInWallet(walletId, symbol) > 0) {
            throw IllegalStateException("Essa ação já está na carteira")
        }
        val quote = try {
            fetchQuote(symbol, Indicators.barsNeeded(config.type, config.period, config.fastPeriod))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException(friendlyError(e))
        }
        val eval = Indicators.evaluate(quote.closes, config.type, config.period, config.fastPeriod)
        dao.insertAsset(
            AssetEntity(
                walletId = walletId,
                symbol = symbol,
                period = config.period,
                maType = MaType.of(config.type),
                fastPeriod = config.fastPeriod,
                name = quote.name,
                currency = quote.currency,
                price = quote.price,
                prevClose = quote.prevClose,
                sma = eval.ma,
                fastMa = eval.fastMa,
                state = eval.state,
                updatedAt = System.currentTimeMillis(),
                chartCloses = Indicators.encode(eval.chartCloses),
                chartMa = Indicators.encode(eval.chartMa),
                chartFast = eval.chartFast?.let { Indicators.encode(it) },
            )
        )
    }

    suspend fun updateAsset(id: Long, config: MaConfig, alertEnabled: Boolean) {
        val current = dao.getAsset(id) ?: return
        val type = MaType.of(config.type)
        val changed = current.period != config.period ||
            MaType.of(current.maType) != type ||
            current.fastPeriod != config.fastPeriod
        // Ao trocar a média, zera o estado para não disparar alerta falso.
        dao.updateAssetSettings(
            id = id,
            period = config.period,
            maType = type,
            fastPeriod = config.fastPeriod,
            alertEnabled = alertEnabled,
            state = if (changed) null else current.state,
        )
        if (changed) refreshSymbols(setOf(current.symbol))
    }

    suspend fun removeAsset(id: Long) = dao.deleteAsset(id)

    // ---------- Atualização ----------

    /** Atualização automática: só durante o pregão ou para registrar o fechamento. */
    suspend fun shouldAutoRefresh(): Boolean {
        val assets = dao.allAssets()
        val hasB3 = assets.any { it.symbol.endsWith(".SA") }
        val hasOther = assets.any { !it.symbol.endsWith(".SA") }
        return MarketHours.shouldAutoRefresh(Instant.now(), _lastUpdate.value, hasB3, hasOther)
    }

    /** Atualiza todas as ações. Se já houver uma atualização em andamento, não faz nada. */
    suspend fun refreshAll() {
        if (!mutex.tryLock()) return
        try {
            refreshInternal(null)
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_UPDATE, now).apply()
            _lastUpdate.value = now
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun refreshSymbols(symbols: Set<String>) {
        if (symbols.isEmpty()) return
        mutex.withLock { refreshInternal(symbols) }
    }

    private suspend fun refreshInternal(only: Set<String>?) {
        _refreshing.value = true
        try {
            val assets = dao.allAssets().filter { only == null || it.symbol in only }
            val walletNames = dao.allWallets().associate { it.id to it.name }
            var first = true

            for ((symbol, group) in assets.groupBy { it.symbol }) {
                if (!first) delay(REQUEST_GAP_MS)
                first = false

                val bars = group.maxOf { Indicators.barsNeeded(it.maType, it.period, it.fastPeriod) }
                val quote: Quote = try {
                    fetchQuote(symbol, bars)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    group.forEach { dao.setError(it.id, friendlyError(e)) }
                    continue
                }

                for (stale in group) {
                    // Relê a ação para usar as configurações mais recentes.
                    val asset = dao.getAsset(stale.id) ?: continue
                    val eval = Indicators.evaluate(quote.closes, asset.maType, asset.period, asset.fastPeriod)
                    val cross = Indicators.crossOf(asset.state, eval.state)

                    if (Indicators.shouldNotify(cross, asset.alertEnabled, _notifyAbove.value) && eval.ma != null) {
                        Notifier.notifyCross(
                            context = context,
                            asset = asset,
                            walletName = walletNames[asset.walletId].orEmpty(),
                            price = quote.price,
                            ma = eval.ma,
                            fastMa = eval.fastMa,
                            currency = quote.currency ?: asset.currency,
                            below = cross == Indicators.Cross.WENT_BELOW,
                        )
                    }

                    dao.updateMarket(
                        id = asset.id,
                        name = quote.name ?: asset.name,
                        currency = quote.currency ?: asset.currency,
                        price = quote.price,
                        prevClose = quote.prevClose,
                        ma = eval.ma,
                        fastMa = eval.fastMa,
                        // Sem histórico suficiente mantém o último estado conhecido.
                        state = eval.state ?: asset.state,
                        updatedAt = System.currentTimeMillis(),
                        chartCloses = Indicators.encode(eval.chartCloses),
                        chartMa = Indicators.encode(eval.chartMa),
                        chartFast = eval.chartFast?.let { Indicators.encode(it) },
                    )
                }
            }
        } finally {
            _refreshing.value = false
        }
    }

    /** Yahoo Finance; para ações da B3, usa a brapi.dev como reserva. */
    private suspend fun fetchQuote(symbol: String, bars: Int): Quote {
        try {
            return yahoo.fetch(symbol, Indicators.rangeFor(bars))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (symbol.endsWith(".SA")) {
                try {
                    return brapi.fetch(symbol, _brapiToken.value.ifBlank { null })
                } catch (ce: CancellationException) {
                    throw ce
                } catch (ignored: Exception) {
                    // mantém o erro original do Yahoo
                }
            }
            throw e
        }
    }

    private fun friendlyError(e: Throwable): String = when (e) {
        is SymbolNotFoundException -> "Ativo não encontrado"
        is UnknownHostException, is ConnectException -> "Sem conexão com a internet"
        is SocketTimeoutException -> "Tempo de conexão esgotado"
        is IOException -> "Não foi possível obter a cotação"
        else -> e.message ?: "Erro inesperado"
    }

    companion object {
        private const val KEY_LAST_UPDATE = "last_update"
        private const val KEY_NOTIFY_ABOVE = "notify_above"
        private const val KEY_BRAPI_TOKEN = "brapi_token"
        private const val REQUEST_GAP_MS = 350L
    }
}

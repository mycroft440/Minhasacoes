package com.minhasacoes.app.data

import android.content.Context
import androidx.room.withTransaction
import com.minhasacoes.app.data.api.Quote
import com.minhasacoes.app.data.api.SymbolNotFoundException
import com.minhasacoes.app.data.api.YahooClient
import com.minhasacoes.app.data.db.AppDatabase
import com.minhasacoes.app.data.db.AssetEntity
import com.minhasacoes.app.data.db.MaState
import com.minhasacoes.app.data.db.WalletEntity
import com.minhasacoes.app.data.db.WalletWithAssets
import com.minhasacoes.app.notify.Notifier
import com.minhasacoes.app.util.ticker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class Repository(
    private val context: Context,
    private val db: AppDatabase,
    private val api: YahooClient,
) {
    private val dao = db.dao()
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _lastUpdate = MutableStateFlow(prefs.getLong(KEY_LAST_UPDATE, 0L))
    val lastUpdate: StateFlow<Long> = _lastUpdate.asStateFlow()

    private val _notifyAbove = MutableStateFlow(prefs.getBoolean(KEY_NOTIFY_ABOVE, false))
    val notifyAbove: StateFlow<Boolean> = _notifyAbove.asStateFlow()

    fun setNotifyAbove(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_ABOVE, enabled).apply()
        _notifyAbove.value = enabled
    }

    fun observeWallets(): Flow<List<WalletWithAssets>> = dao.observeWallets()

    fun observeWallet(id: Long): Flow<WalletWithAssets?> = dao.observeWallet(id)

    // ---------- Carteiras ----------

    suspend fun createWallet(name: String, period: Int): Long =
        dao.insertWallet(WalletEntity(name = name.trim(), defaultPeriod = period))

    suspend fun renameWallet(id: Long, name: String) = dao.renameWallet(id, name.trim())

    /** Altera a média padrão e aplica a todos os ativos da carteira. */
    suspend fun setWalletPeriod(id: Long, period: Int) {
        db.withTransaction {
            dao.setWalletPeriod(id, period)
            dao.setPeriodForWallet(id, period)
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

    /** Valida o ticker buscando a cotação e adiciona à carteira. Lança exceção com mensagem amigável. */
    suspend fun addAsset(walletId: Long, input: String, period: Int) {
        val symbol = YahooClient.normalize(input)
        if (symbol.isBlank()) throw IllegalArgumentException("Digite um ticker")
        if (dao.countInWallet(walletId, symbol) > 0) {
            throw IllegalStateException("Esse ativo já está na carteira")
        }
        val quote = try {
            api.fetch(symbol, YahooClient.rangeFor(period))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException(friendlyError(e))
        }
        val sma = quote.sma(period)
        dao.insertAsset(
            AssetEntity(
                walletId = walletId,
                symbol = symbol,
                period = period,
                name = quote.name,
                currency = quote.currency,
                price = quote.price,
                prevClose = quote.prevClose,
                sma = sma,
                state = stateOf(quote.price, sma),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun updateAsset(id: Long, period: Int, alertEnabled: Boolean) {
        val current = dao.getAsset(id) ?: return
        val periodChanged = current.period != period
        // Ao trocar o período, zera o estado para não disparar alerta falso.
        dao.updateAssetSettings(
            id = id,
            period = period,
            alertEnabled = alertEnabled,
            state = if (periodChanged) null else current.state,
        )
        if (periodChanged) refreshSymbols(setOf(current.symbol))
    }

    suspend fun removeAsset(id: Long) = dao.deleteAsset(id)

    // ---------- Atualização ----------

    /** Atualiza todos os ativos. Se já houver uma atualização em andamento, não faz nada. */
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

                val maxPeriod = group.maxOf { it.period }
                val quote: Quote = try {
                    api.fetch(symbol, YahooClient.rangeFor(maxPeriod))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    group.forEach { dao.setError(it.id, friendlyError(e)) }
                    continue
                }

                for (stale in group) {
                    // Relê o ativo para usar as configurações mais recentes.
                    val asset = dao.getAsset(stale.id) ?: continue
                    val sma = quote.sma(asset.period)
                    val newState = stateOf(quote.price, sma)

                    val crossed = asset.state != null && newState != null && asset.state != newState
                    if (crossed && asset.alertEnabled && sma != null) {
                        val wentBelow = newState == MaState.BELOW
                        if (wentBelow || _notifyAbove.value) {
                            Notifier.notifyCross(
                                context = context,
                                assetId = asset.id,
                                walletId = asset.walletId,
                                ticker = asset.ticker,
                                walletName = walletNames[asset.walletId].orEmpty(),
                                price = quote.price,
                                sma = sma,
                                period = asset.period,
                                currency = quote.currency ?: asset.currency,
                                below = wentBelow,
                            )
                        }
                    }

                    dao.updateMarket(
                        id = asset.id,
                        name = quote.name ?: asset.name,
                        currency = quote.currency ?: asset.currency,
                        price = quote.price,
                        prevClose = quote.prevClose,
                        sma = sma,
                        // Sem histórico suficiente mantém o último estado conhecido.
                        state = newState ?: asset.state,
                        updatedAt = System.currentTimeMillis(),
                    )
                }
            }
        } finally {
            _refreshing.value = false
        }
    }

    private fun stateOf(price: Double, sma: Double?): String? = when {
        sma == null -> null
        price < sma -> MaState.BELOW
        else -> MaState.ABOVE
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
        private const val REQUEST_GAP_MS = 350L
    }
}

package com.minhasacoes.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    // Carteiras

    @Transaction
    @Query("SELECT * FROM wallets ORDER BY createdAt ASC")
    fun observeWallets(): Flow<List<WalletWithAssets>>

    @Transaction
    @Query("SELECT * FROM wallets ORDER BY createdAt ASC")
    suspend fun walletsOnce(): List<WalletWithAssets>

    @Transaction
    @Query("SELECT * FROM wallets WHERE id = :id")
    fun observeWallet(id: Long): Flow<WalletWithAssets?>

    @Query("SELECT * FROM wallets")
    suspend fun allWallets(): List<WalletEntity>

    @Insert
    suspend fun insertWallet(wallet: WalletEntity): Long

    @Query("UPDATE wallets SET name = :name WHERE id = :id")
    suspend fun renameWallet(id: Long, name: String)

    @Query("UPDATE wallets SET defaultPeriod = :period, defaultMaType = :maType WHERE id = :id")
    suspend fun setWalletDefaults(id: Long, period: Int, maType: String)

    @Query("DELETE FROM wallets WHERE id = :id")
    suspend fun deleteWallet(id: Long)

    // Ativos

    @Query("SELECT * FROM assets")
    suspend fun allAssets(): List<AssetEntity>

    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun getAsset(id: Long): AssetEntity?

    @Query("SELECT * FROM assets WHERE walletId = :walletId")
    suspend fun assetsOfWallet(walletId: Long): List<AssetEntity>

    @Query("SELECT COUNT(*) FROM assets WHERE walletId = :walletId AND symbol = :symbol")
    suspend fun countInWallet(walletId: Long, symbol: String): Int

    @Insert
    suspend fun insertAsset(asset: AssetEntity): Long

    @Query(
        "UPDATE assets SET period = :period, maType = :maType, fastPeriod = :fastPeriod, " +
            "alertEnabled = :alertEnabled, state = :state WHERE id = :id"
    )
    suspend fun updateAssetSettings(
        id: Long,
        period: Int,
        maType: String,
        fastPeriod: Int?,
        alertEnabled: Boolean,
        state: String?,
    )

    /** Aplica a média padrão a todas as ações; a média rápida só é mantida se continuar menor. */
    @Query(
        "UPDATE assets SET period = :period, maType = :maType, state = NULL, " +
            "fastPeriod = CASE WHEN fastPeriod IS NOT NULL AND fastPeriod < :period THEN fastPeriod ELSE NULL END " +
            "WHERE walletId = :walletId"
    )
    suspend fun applyDefaultsToWallet(walletId: Long, period: Int, maType: String)

    @Query(
        "UPDATE assets SET name = :name, currency = :currency, price = :price, prevClose = :prevClose, " +
            "sma = :ma, fastMa = :fastMa, state = :state, updatedAt = :updatedAt, error = NULL, " +
            "chartCloses = :chartCloses, chartMa = :chartMa, chartFast = :chartFast WHERE id = :id"
    )
    suspend fun updateMarket(
        id: Long,
        name: String?,
        currency: String?,
        price: Double,
        prevClose: Double?,
        ma: Double?,
        fastMa: Double?,
        state: String?,
        updatedAt: Long,
        chartCloses: String?,
        chartMa: String?,
        chartFast: String?,
    )

    @Query("UPDATE assets SET monthly = :monthly, monthlyAt = :at WHERE symbol = :symbol")
    suspend fun setMonthly(symbol: String, monthly: String, at: Long)

    @Query("UPDATE assets SET error = :error WHERE id = :id")
    suspend fun setError(id: Long, error: String)

    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun deleteAsset(id: Long)

    @Query("DELETE FROM assets WHERE walletId = :walletId")
    suspend fun deleteAssetsOfWallet(walletId: Long)
}

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
    @Query("SELECT * FROM wallets WHERE id = :id")
    fun observeWallet(id: Long): Flow<WalletWithAssets?>

    @Query("SELECT * FROM wallets")
    suspend fun allWallets(): List<WalletEntity>

    @Insert
    suspend fun insertWallet(wallet: WalletEntity): Long

    @Query("UPDATE wallets SET name = :name WHERE id = :id")
    suspend fun renameWallet(id: Long, name: String)

    @Query("UPDATE wallets SET defaultPeriod = :period WHERE id = :id")
    suspend fun setWalletPeriod(id: Long, period: Int)

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

    @Query("UPDATE assets SET period = :period, alertEnabled = :alertEnabled, state = :state WHERE id = :id")
    suspend fun updateAssetSettings(id: Long, period: Int, alertEnabled: Boolean, state: String?)

    @Query("UPDATE assets SET period = :period, state = NULL WHERE walletId = :walletId")
    suspend fun setPeriodForWallet(walletId: Long, period: Int)

    @Query(
        "UPDATE assets SET name = :name, currency = :currency, price = :price, prevClose = :prevClose, " +
            "sma = :sma, state = :state, updatedAt = :updatedAt, error = NULL WHERE id = :id"
    )
    suspend fun updateMarket(
        id: Long,
        name: String?,
        currency: String?,
        price: Double,
        prevClose: Double?,
        sma: Double?,
        state: String?,
        updatedAt: Long,
    )

    @Query("UPDATE assets SET error = :error WHERE id = :id")
    suspend fun setError(id: Long, error: String)

    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun deleteAsset(id: Long)

    @Query("DELETE FROM assets WHERE walletId = :walletId")
    suspend fun deleteAssetsOfWallet(walletId: Long)
}

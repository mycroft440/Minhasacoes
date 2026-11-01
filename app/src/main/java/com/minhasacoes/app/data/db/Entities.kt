package com.minhasacoes.app.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val defaultPeriod: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "assets",
    indices = [Index("walletId"), Index("symbol")],
)
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val walletId: Long,
    /** Símbolo usado na consulta (ex.: PETR4.SA, AAPL). */
    val symbol: String,
    /** Período da média móvel simples, em pregões. */
    val period: Int,
    val alertEnabled: Boolean = true,
    val name: String? = null,
    val currency: String? = null,
    val price: Double? = null,
    val prevClose: Double? = null,
    val sma: Double? = null,
    /** "ABOVE" ou "BELOW" — última posição conhecida em relação à média. */
    val state: String? = null,
    val updatedAt: Long? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class WalletWithAssets(
    @Embedded val wallet: WalletEntity,
    @Relation(parentColumn = "id", entityColumn = "walletId")
    val assets: List<AssetEntity>,
)

object MaState {
    const val ABOVE = "ABOVE"
    const val BELOW = "BELOW"
}

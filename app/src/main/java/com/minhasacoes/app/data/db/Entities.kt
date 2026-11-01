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
    /** "SMA" ou "EMA" (null = SMA). */
    val defaultMaType: String? = null,
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
    /** Período da média principal, em pregões. */
    val period: Int,
    val alertEnabled: Boolean = true,
    val name: String? = null,
    val currency: String? = null,
    val price: Double? = null,
    val prevClose: Double? = null,
    /** Valor da média principal. */
    val sma: Double? = null,
    /** "ABOVE" ou "BELOW" — última posição conhecida. */
    val state: String? = null,
    val updatedAt: Long? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    // v2
    /** "SMA" ou "EMA" (null = SMA). */
    val maType: String? = null,
    /** Período da média rápida; quando definido, compara média rápida x média principal. */
    val fastPeriod: Int? = null,
    val fastMa: Double? = null,
    /** Séries do gráfico (últimos pregões), em CSV. */
    val chartCloses: String? = null,
    val chartMa: String? = null,
    val chartFast: String? = null,
)

data class WalletWithAssets(
    @Embedded val wallet: WalletEntity,
    @Relation(parentColumn = "id", entityColumn = "walletId")
    val assets: List<AssetEntity>,
)

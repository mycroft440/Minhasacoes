package com.minhasacoes.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [WalletEntity::class, AssetEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao
}

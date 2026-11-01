package com.minhasacoes.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WalletEntity::class, AssetEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        /** v1 -> v2: tipo de média, cruzamento de médias e séries do gráfico. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE assets ADD COLUMN maType TEXT")
                db.execSQL("ALTER TABLE assets ADD COLUMN fastPeriod INTEGER")
                db.execSQL("ALTER TABLE assets ADD COLUMN fastMa REAL")
                db.execSQL("ALTER TABLE assets ADD COLUMN chartCloses TEXT")
                db.execSQL("ALTER TABLE assets ADD COLUMN chartMa TEXT")
                db.execSQL("ALTER TABLE assets ADD COLUMN chartFast TEXT")
                db.execSQL("ALTER TABLE wallets ADD COLUMN defaultMaType TEXT")
            }
        }
    }
}

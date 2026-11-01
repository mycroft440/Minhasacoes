package com.minhasacoes.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.minhasacoes.app.data.Repository
import com.minhasacoes.app.data.db.AppDatabase
import com.minhasacoes.app.notify.Notifier
import com.minhasacoes.app.widget.WidgetUpdater
import com.minhasacoes.app.work.RefreshWorker
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class App : Application() {

    /** Escopo do app: operações iniciadas pela interface continuam mesmo se a tela fechar. */
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> }
    )

    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "minhasacoes.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
        repository = Repository(this, db)
        Notifier.createChannel(this)
        RefreshWorker.schedule(this)

        // Mantém o widget em dia com qualquer mudança nas carteiras ou nas cotações.
        appScope.launch {
            combine(repository.observeWallets(), repository.lastUpdate) { wallets, last -> wallets to last }
                .collectLatest { (wallets, last) ->
                    delay(400)
                    WidgetUpdater.render(this@App, wallets, last)
                }
        }
    }
}

val Context.app: App get() = applicationContext as App

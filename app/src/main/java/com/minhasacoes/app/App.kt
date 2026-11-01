package com.minhasacoes.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.minhasacoes.app.data.Repository
import com.minhasacoes.app.data.api.YahooClient
import com.minhasacoes.app.data.db.AppDatabase
import com.minhasacoes.app.notify.Notifier
import com.minhasacoes.app.work.RefreshWorker
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {

    /** Escopo do app: operações iniciadas pela interface continuam mesmo se a tela fechar. */
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> }
    )

    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "minhasacoes.db").build()
        repository = Repository(this, db, YahooClient())
        Notifier.createChannel(this)
        RefreshWorker.schedule(this)
    }
}

val Context.app: App get() = applicationContext as App

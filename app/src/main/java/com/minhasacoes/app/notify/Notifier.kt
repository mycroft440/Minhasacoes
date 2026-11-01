package com.minhasacoes.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.minhasacoes.app.MainActivity
import com.minhasacoes.app.R
import com.minhasacoes.app.util.money

object Notifier {

    private const val CHANNEL_ID = "ma_cross_alerts"
    const val EXTRA_WALLET_ID = "wallet_id"

    private val COLOR_UP = 0xFF2BD38A.toInt()
    private val COLOR_DOWN = 0xFFFF5A67.toInt()

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alertas de média móvel",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Avisa quando uma ação cruza a média móvel definida"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notifyCross(
        context: Context,
        assetId: Long,
        walletId: Long,
        ticker: String,
        walletName: String,
        price: Double,
        sma: Double,
        period: Int,
        currency: String?,
        below: Boolean,
    ) {
        val title = if (below) "$ticker ficou abaixo da MM$period" else "$ticker voltou acima da MM$period"
        val text = buildString {
            append(money(price, currency))
            append(" · média ")
            append(money(sma, currency))
            if (walletName.isNotBlank()) append(" · ").append(walletName)
        }
        show(context, assetId.toInt(), walletId, title, text, if (below) COLOR_DOWN else COLOR_UP)
    }

    fun sendTest(context: Context) {
        show(
            context = context,
            id = 0,
            walletId = null,
            title = "Notificações ativadas",
            text = "Você será avisado quando uma ação cruzar a média.",
            color = COLOR_UP,
        )
    }

    @SuppressLint("MissingPermission")
    private fun show(context: Context, id: Int, walletId: Long?, title: String, text: String, color: Int) {
        if (!canNotify(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (walletId != null) putExtra(EXTRA_WALLET_ID, walletId)
        }
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_trend)
            .setContentTitle(title)
            .setContentText(text)
            .setColor(color)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // permissão revogada entre a checagem e o envio
        }
    }
}

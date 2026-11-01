package com.minhasacoes.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.minhasacoes.app.MainActivity
import com.minhasacoes.app.R
import com.minhasacoes.app.app
import com.minhasacoes.app.data.db.WalletWithAssets
import com.minhasacoes.app.notify.Notifier
import com.minhasacoes.app.util.pct
import com.minhasacoes.app.util.summary
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Widget da tela inicial com o resumo das carteiras. */
class WalletWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        val app = context.app
        app.appScope.launch {
            try {
                WidgetUpdater.render(
                    context = app,
                    wallets = app.repository.walletsSnapshot(),
                    lastUpdate = app.repository.lastUpdate.value,
                )
            } finally {
                pending.finish()
            }
        }
    }
}

object WidgetUpdater {

    private val ROWS = intArrayOf(R.id.widget_row_0, R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3)
    private val NAMES = intArrayOf(R.id.widget_name_0, R.id.widget_name_1, R.id.widget_name_2, R.id.widget_name_3)
    private val PCTS = intArrayOf(R.id.widget_pct_0, R.id.widget_pct_1, R.id.widget_pct_2, R.id.widget_pct_3)
    private val BREADTHS = intArrayOf(
        R.id.widget_breadth_0, R.id.widget_breadth_1, R.id.widget_breadth_2, R.id.widget_breadth_3,
    )

    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    private val DATE = DateTimeFormatter.ofPattern("dd/MM HH:mm")

    fun render(context: Context, wallets: List<WalletWithAssets>, lastUpdate: Long) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, WalletWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val views = RemoteViews(context.packageName, R.layout.widget_wallets)
        views.setTextViewText(R.id.widget_updated, shortTime(lastUpdate))
        views.setOnClickPendingIntent(R.id.widget_root, openIntent(context, null, 900))
        views.setViewVisibility(R.id.widget_empty, if (wallets.isEmpty()) View.VISIBLE else View.GONE)

        for (i in ROWS.indices) {
            val w = wallets.getOrNull(i)
            if (w == null) {
                views.setViewVisibility(ROWS[i], View.GONE)
                continue
            }
            val s = w.summary()
            views.setViewVisibility(ROWS[i], View.VISIBLE)
            views.setTextViewText(NAMES[i], w.wallet.name)
            views.setTextViewText(PCTS[i], pct(s.dayChange))
            views.setTextViewText(
                BREADTHS[i],
                if (s.total == 0) "Sem ações" else "${s.above} acima · ${s.below} abaixo",
            )
            val colorRes = when {
                s.dayChange == null || s.dayChange == 0.0 -> R.color.widget_muted
                s.dayChange > 0 -> R.color.widget_up
                else -> R.color.widget_down
            }
            setTextColorRes(context, views, PCTS[i], colorRes)
            views.setOnClickPendingIntent(ROWS[i], openIntent(context, w.wallet.id, 901 + i))
        }

        manager.updateAppWidget(ids, views)
    }

    /** No Android 12+ a cor é resolvida pelo launcher (respeita tema claro/escuro dele). */
    private fun setTextColorRes(context: Context, views: RemoteViews, viewId: Int, colorRes: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColor(viewId, "setTextColor", colorRes)
        } else {
            views.setTextColor(viewId, ContextCompat.getColor(context, colorRes))
        }
    }

    private fun openIntent(context: Context, walletId: Long?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "open_from_widget_$requestCode"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (walletId != null) putExtra(Notifier.EXTRA_WALLET_ID, walletId)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun shortTime(millis: Long): String {
        if (millis <= 0L) return ""
        val zone = ZoneId.systemDefault()
        val dt = Instant.ofEpochMilli(millis).atZone(zone)
        return if (dt.toLocalDate() == LocalDate.now(zone)) dt.format(TIME) else dt.format(DATE)
    }
}

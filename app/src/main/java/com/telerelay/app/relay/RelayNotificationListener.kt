package com.telerelay.app.relay

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.telerelay.app.R
import com.telerelay.app.data.NotificationRecord
import com.telerelay.app.data.Store
import com.telerelay.app.telegram.Bot
import com.telerelay.app.telegram.TelegramClient
import java.util.concurrent.Executors

/**
 * System notification listener. Every notification posted on the device lands
 * here; selected ones are formatted and relayed to the owner's Telegram chat.
 */
class RelayNotificationListener : NotificationListenerService() {

    private val executor = Executors.newSingleThreadExecutor()
    private var lastKey = ""
    private var lastAt = 0L

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            Store.init(this)
            if (!Store.enabled.value || !Store.configured.value) return

            val pkg = sbn.packageName
            if (pkg == packageName) return
            if (pkg !in Store.selectedApps.value) return
            if (Store.pausedNow()) return
            if (sbn.isOngoing) return

            val n: Notification = sbn.notification ?: return
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

            val ex = n.extras
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            var text = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
            if (text.isBlank()) text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            if (title.isBlank() && text.isBlank()) return

            // collapse duplicate reposts within a short window
            val now = System.currentTimeMillis()
            val key = "$pkg|$title|$text"
            if (key == lastKey && now - lastAt < 2500) return
            lastKey = key
            lastAt = now

            val appLabel = Store.labelFor(applicationContext, pkg)
            if (Store.isMuted(appLabel)) return

            val whenMs = if (sbn.postTime in 1 until now) sbn.postTime else now

            executor.execute {
                val chatId = Store.ownerChatId.value
                val html = Bot.forwardMessage(
                    Store.deviceName.value, appLabel, title, text, whenMs,
                    Store.includeTimestamp.value
                )
                var ok = false
                if (chatId > 0) {
                    ok = try {
                        TelegramClient.sendMessage(Store.botToken.value, chatId, html)
                        true
                    } catch (e: Exception) { false }
                }
                Store.addRecent(NotificationRecord(appLabel, pkg, title, text, whenMs, ok))
            }
        } catch (_: Exception) {
            // never crash the system's listener process
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
    }

    companion object {
        fun isEnabled(context: Context): Boolean =
            androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)

        fun openAccessSettings(context: Context) {
            context.startActivity(
                Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

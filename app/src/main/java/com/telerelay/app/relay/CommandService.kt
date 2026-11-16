package com.telerelay.app.relay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.telerelay.app.R
import com.telerelay.app.data.RelayState
import com.telerelay.app.data.Store
import com.telerelay.app.telegram.Bot
import com.telerelay.app.telegram.TelegramClient

/**
 * Foreground service that polls Telegram getUpdates so the bot can answer
 * commands. Multiple phones can share one bot token: updates are never
 * offset-confirmed by us, so every device sees every command and answers for
 * itself (status, apps, recent…) or only when it is the addressed device.
 * Replies are sent exclusively to the owner chat learned from the first
 * private-chat message.
 */
class CommandService : Service() {

    private var running = false
    private var thread: Thread? = null
    private val seen = HashSet<Long>()
    private var lastSeenMax = 0L
    private var firstBatch = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        createChannel()
        synchronized(seen) {
            seen.addAll(Store.loadSeen())
            lastSeenMax = Store.prefs.getLong("last_update_max", 0L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        CommandService.isRunning = true
        if (!running) {
            running = true
            thread = Thread({ loop() }, "telerelay-poll").also { it.start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        CommandService.isRunning = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // ---------- polling ----------

    private fun loop() {
        while (running) {
            try {
                val token = Store.botToken.value
                if (token.isBlank() || !Store.enabled.value) {
                    Thread.sleep(4000)
                    continue
                }
                poll(token)
                Thread.sleep(5000)
            } catch (e: TelegramClient.TgException) {
                // 409 usually means a webhook is set (or another getUpdates ran
                // concurrently). Removing the webhook fixes the common case.
                if (e.code == 409) {
                    try { TelegramClient.deleteWebhook(Store.botToken.value) } catch (_: Exception) {}
                }
                sleepQuiet(8000)
            } catch (e: Exception) {
                sleepQuiet(8000) // offline, keep retrying
            }
        }
    }

    private fun sleepQuiet(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) {}
    }

    private fun poll(token: String) {
        val updates = TelegramClient.getUpdates(token)
        if (updates.length() == 0) return
        for (i in 0 until updates.length()) {
            val u = updates.getJSONObject(i)
            val id = u.optLong("update_id", -1L)
            if (id <= 0) continue
            synchronized(seen) {
                if (id <= lastSeenMax || seen.contains(id)) return@synchronized
                seen.add(id)
                if (id > lastSeenMax) lastSeenMax = id
                if (firstBatch) bindOwner(u) else handleUpdate(u)
            }
        }
        firstBatch = false
        Store.saveSeen(seen)
        Store.prefs.edit().putLong("last_update_max", lastSeenMax).apply()
    }

    /** First poll after launch: only link the owner chat, never replay old commands. */
    private fun bindOwner(u: org.json.JSONObject) {
        val msg = u.optJSONObject("message") ?: return
        val chat = msg.optJSONObject("chat") ?: return
        if (chat.optString("type") != "private") return
        val chatId = chat.optLong("id", -1L)
        if (chatId <= 0) return
        Store.setOwnerChat(chatId)
        safeReply(chatId, welcome(chatId))
    }

    private fun welcome(chatId: Long): String {
        val device = Bot.esc(Store.deviceName.value.ifBlank { "Device" })
        return "✅ <b>$device linked to TeleRelay</b>\n\n" +
            "This device will relay notifications from its selected apps to this chat.\n\n" +
            "Send /help to see everything I can do, or /devices to list every connected phone."
    }

    private fun handleUpdate(u: org.json.JSONObject) {
        try {
            val msg = u.optJSONObject("message") ?: return
            val chat = msg.optJSONObject("chat") ?: return
            if (chat.optString("type") != "private") return // bot only serves its owner
            val chatId = chat.optLong("id", -1L)
            if (chatId <= 0) return

            val owner = Store.ownerChatId.value
            if (owner <= 0) {
                Store.setOwnerChat(chatId)
                safeReply(chatId, welcome(chatId))
                return
            }
            if (chatId != owner) return // silently ignore everyone else

            val text = msg.optString("text", msg.optString("caption", "")).trim()
            if (text.isEmpty()) return

            val parsed = Bot.parse(text)
            if (parsed == null) {
                safeReply(chatId, "💬 TeleRelay only understands commands.\nSend /help to see the full list.")
                return
            }
            dispatch(chatId, parsed.command, parsed.args)
        } catch (_: Exception) {}
    }

    private fun dispatch(chatId: Long, command: String, args: List<String>) {
        val device = Store.deviceName.value.ifBlank { "Device" }
        val d = Bot.esc(device)
        val reply: String? = when (command) {
            "start" -> welcome(chatId)

            "help", "commands" -> Bot.help(device)

            "devices", "status" -> Bot.statusLine()

            "apps" -> Bot.appsList()

            "recent" -> Bot.recentList(args.joinToString(" ").ifBlank { null })

            "search" -> {
                val q = args.joinToString(" ")
                if (q.isBlank()) "🔍 Usage: /search <b>text</b>"
                else Bot.searchList(q)
            }

            "pause" -> {
                val mins = args.firstOrNull()?.toLongOrNull()
                if (mins != null && mins > 0) {
                    Store.pauseFor(mins * 60_000L)
                    "⏸ <b>$d</b> paused for ${Bot.humanDuration(mins)}.\nSend /resume to continue early."
                } else {
                    Store.pauseFor(0L)
                    "⏸ <b>$d</b> paused until you send /resume."
                }
            }

            "resume" -> {
                Store.resume()
                "▶️ <b>$d</b> resumed — forwarding is live again."
            }

            "mute" -> {
                val q = args.joinToString(" ")
                if (q.isBlank()) "🔇 Usage: /mute <b>app name</b>"
                else {
                    val matches = Store.selectedApps.value.filter {
                        val l = (Store.appLabels[it] ?: it)
                        l.contains(q, ignoreCase = true) || q.contains(l, ignoreCase = true)
                    }
                    if (matches.isEmpty()) {
                        "❓ <b>$d</b> — no selected app matches “${Bot.esc(q)}”."
                    } else {
                        val muted = Store.mutedApps.value.toMutableSet()
                        matches.forEach { muted.add((Store.appLabels[it] ?: it).lowercase()) }
                        Store.setMuted(muted)
                        "🔇 <b>$d</b> muted: " + matches.map {
                            Bot.esc(Store.appLabels[it] ?: it)
                        }.joinToString(", ")
                    }
                }
            }

            "unmute" -> {
                val q = args.joinToString(" ")
                if (q.isBlank()) {
                    val n = Store.mutedApps.value.size
                    Store.setMuted(emptySet())
                    "🔊 <b>$d</b> unmuted everything ($n apps restored)."
                } else {
                    val muted = Store.mutedApps.value.toMutableSet()
                    val removed = muted.filter { it.contains(q, ignoreCase = true) || q.contains(it, ignoreCase = true) }
                    if (removed.isEmpty()) {
                        "❓ <b>$d</b> — nothing was muted for “${Bot.esc(q)}”."
                    } else {
                        muted.removeAll(removed.toSet())
                        Store.setMuted(muted)
                        "🔊 <b>$d</b> unmuted: " + removed.joinToString(", ") { Bot.esc(it) }
                    }
                }
            }

            "test" -> Bot.testMessage(device, System.currentTimeMillis())

            "ping" -> "🏓 Pong — <b>$d</b> is alive."

            "id" -> "🆔 Chat ID: <code>$chatId</code>\n\n" +
                "Only this chat receives notifications and command replies."

            else -> "🤔 Unknown command <i>/${Bot.esc(command)}</i>.\nSend /help to see the full list."
        }
        if (reply != null) safeReply(chatId, reply)
    }

    private fun safeReply(chatId: Long, html: String) {
        try {
            TelegramClient.sendMessage(Store.botToken.value, chatId, html)
        } catch (_: Exception) {}
    }

    // ---------- foreground notification ----------

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(CHANNEL_ID, getString(R.string.channel_service), NotificationManager.IMPORTANCE_LOW)
        ch.description = getString(R.string.channel_service_desc)
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)
    }

    private fun goForeground() {
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("TeleRelay is active")
            .setContentText("Relaying notifications · listening for bot commands")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        ServiceCompat.startForeground(this, NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        private const val CHANNEL_ID = "relay_service"
        private const val NOTIF_ID = 1001

        @Volatile var isRunning = false

        fun start(context: Context) {
            context.startForegroundService(Intent(context, CommandService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CommandService::class.java))
        }

        fun shouldRun(): Boolean =
            Store.configured.value && Store.enabled.value && Store.botToken.value.isNotBlank()
    }
}

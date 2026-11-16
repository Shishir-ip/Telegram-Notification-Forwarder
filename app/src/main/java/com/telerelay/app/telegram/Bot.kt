package com.telerelay.app.telegram

import com.telerelay.app.data.NotificationRecord
import com.telerelay.app.data.RelayState
import com.telerelay.app.data.Store
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds every HTML message the bot sends, and handles incoming commands. */
object Bot {

    fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    // ---------- outgoing message formats ----------

    fun forwardMessage(
        device: String,
        app: String,
        title: String,
        text: String,
        ts: Long,
        includeTimestamp: Boolean
    ): String {
        val sb = StringBuilder()
        sb.append("📱 <b>").append(esc(device.ifBlank { "Device" })).append("</b> · ").append(esc(app))
        sb.append("\n")
        if (title.isNotBlank()) sb.append("🔔 <b>").append(esc(title)).append("</b>\n")
        val body = if (text.isNotBlank() && text != title) text.take(3000) else ""
        if (body.isNotBlank()) sb.append("\n").append(esc(body)).append("\n")
        if (includeTimestamp) {
            sb.append("\n🕐 ").append(
                SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ts))
            )
        }
        return sb.toString().trimEnd()
    }

    fun testMessage(device: String, ts: Long): String = forwardMessage(
        device = device,
        app = "TeleRelay",
        title = "Test notification",
        text = "This is a test notification sent from $device. " +
            "If you can read this, your relay is working perfectly. 🎉",
        ts = ts,
        includeTimestamp = true
    )

    fun help(device: String): String = """
        |🤖 <b>TeleRelay Commands</b>  ·  <i>${esc(device)}</i>
        |
        |<b>/devices</b> — status of every connected device
        |<b>/apps</b> — apps this device forwards
        |<b>/recent</b> [app] — last forwarded notifications
        |<b>/search</b> &lt;text&gt; — search forwarded notifications
        |<b>/pause</b> [minutes] — pause forwarding on all devices
        |<b>/resume</b> — resume forwarding everywhere
        |<b>/mute</b> &lt;app&gt; — stop forwarding an app
        |<b>/unmute</b> &lt;app&gt; — resume forwarding an app
        |<b>/test</b> — send a test notification
        |<b>/ping</b> — check this device is alive
        |<b>/id</b> — show this chat's ID
        |
        |<i>Only the owner of this bot can use these commands.</i>
    """.trimMargin()

    fun statusLine(): String {
        val device = esc(Store.deviceName.value.ifBlank { "Device" })
        val apps = Store.selectedApps.value.size
        val today = Store.statsToday.value
        return when (Store.relayState()) {
            RelayState.ACTIVE ->
                "🟢 <b>$device</b> — Active · $apps apps · $today forwarded today"
            RelayState.PAUSED -> {
                val mins = ((Store.pausedUntil.value - System.currentTimeMillis()) / 60000L).coerceAtLeast(1)
                "🟡 <b>$device</b> — Paused (${humanDuration(mins)}) · $apps apps"
            }
            RelayState.OFF ->
                "⚪️ <b>$device</b> — Forwarding off · $apps apps"
            RelayState.WAITING_LINK ->
                "🟠 <b>$device</b> — Waiting for /start to link this chat"
            RelayState.SETUP ->
                "🔴 <b>$device</b> — Setup incomplete (open the TeleRelay app)"
        }
    }

    fun humanDuration(minutes: Long): String = when {
        minutes >= 1440 -> "${minutes / 1440}d ${minutes % 1440 / 60}h"
        minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
        else -> "${minutes}m"
    }

    fun recentList(filter: String?): String {
        val device = esc(Store.deviceName.value.ifBlank { "Device" })
        val all = Store.recent.value
        val items = if (filter.isNullOrBlank()) all
        else all.filter { it.appLabel.contains(filter, ignoreCase = true) }
        if (items.isEmpty()) {
            return "🕘 <b>$device</b> — no forwarded notifications${if (filter.isNullOrBlank()) " yet" else " for “${esc(filter)}”"}."
        }
        val fmt = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
        val sb = StringBuilder("🕘 <b>Recent — $device</b>${if (!filter.isNullOrBlank()) " · ${esc(filter)}" else ""}\n\n")
        items.take(10).forEachIndexed { i, r: NotificationRecord ->
            val snippet = (if (r.title.isNotBlank()) "${r.title} — " else "") + r.text
            sb.append("<b>${i + 1}.</b> ${esc(r.appLabel)} · ${esc(snippet.take(64))}")
            sb.append("\n   <i>").append(fmt.format(Date(r.time))).append("</i>\n\n")
        }
        if (items.size > 10) sb.append("<i>…and ${items.size - 10} more</i>")
        return sb.toString().trimEnd()
    }

    fun searchList(query: String): String {
        val device = esc(Store.deviceName.value.ifBlank { "Device" })
        val matches = Store.recent.value.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.text.contains(query, ignoreCase = true) ||
                it.appLabel.contains(query, ignoreCase = true)
        }
        if (matches.isEmpty()) return "🔍 <b>$device</b> — no matches for “${esc(query)}” in recent notifications."
        val fmt = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
        val sb = StringBuilder("🔍 <b>Matches on $device</b>\n\n")
        matches.take(10).forEach { r ->
            sb.append("• ${esc(r.appLabel)} · ${esc((r.title + " " + r.text).take(72))}")
            sb.append("\n   <i>").append(fmt.format(Date(r.time))).append("</i>\n\n")
        }
        return sb.toString().trimEnd()
    }

    fun appsList(): String {
        val device = esc(Store.deviceName.value.ifBlank { "Device" })
        val apps = Store.selectedApps.value
        if (apps.isEmpty()) return "📭 <b>$device</b> — no apps selected yet.\nOpen TeleRelay → Apps and pick some."
        val sorted = apps.map { Store.appLabels[it] ?: it }.sorted()
        val shown = sorted.take(40)
        val sb = StringBuilder("📲 <b>Apps on $device</b> (${apps.size})\n\n")
        shown.forEach { sb.append("• ").append(esc(it)).append("\n") }
        if (sorted.size > 40) sb.append("\n<i>…and ${sorted.size - 40} more</i>")
        return sb.toString().trimEnd()
    }

    // ---------- command parsing ----------

    data class Parsed(val command: String, val args: List<String>)

    fun parse(text: String): Parsed? {
        val t = text.trim()
        if (!t.startsWith("/")) return null
        val parts = t.split(Regex("\\s+"))
        val cmd = parts[0].removePrefix("/").substringBefore("@").lowercase(Locale.US)
        if (cmd.isBlank()) return null
        return Parsed(cmd, parts.drop(1))
    }
}

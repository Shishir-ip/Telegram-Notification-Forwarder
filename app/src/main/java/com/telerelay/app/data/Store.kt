package com.telerelay.app.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class ThemeMode(val label: String) { LIGHT("Light"), DARK("Dark"), AUTO("Auto") }

enum class RelayState { SETUP, WAITING_LINK, OFF, PAUSED, ACTIVE }

data class NotificationRecord(
    val appLabel: String,
    val packageName: String,
    val title: String,
    val text: String,
    val time: Long,
    val ok: Boolean
)

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?
)

/** Single source of truth. Backed by SharedPreferences, observed by Compose. */
object Store {

    private var prefsRef: SharedPreferences? = null
    val prefs: SharedPreferences get() = prefsRef!!

    fun init(context: Context) {
        if (prefsRef != null) return
        prefsRef = context.applicationContext
            .getSharedPreferences("telerelay", Context.MODE_PRIVATE)
        load()
    }

    // ---------- reactive state ----------
    var botToken = mutableStateOf("")
    var botUsername = mutableStateOf("")
    var deviceName = mutableStateOf("")
    var themeMode = mutableStateOf(ThemeMode.AUTO)
    var enabled = mutableStateOf(true)
    var configured = mutableStateOf(false)
    var selectedApps = mutableStateOf<Set<String>>(emptySet())
    var mutedApps = mutableStateOf<Set<String>>(emptySet()) // lowercased app labels
    var ownerChatId = mutableStateOf(-1L)
    var pausedUntil = mutableStateOf(0L)
    var includeTimestamp = mutableStateOf(true)
    var statsToday = mutableStateOf(0)
    var statsTotal = mutableStateOf(0)
    var recent = mutableStateOf<List<NotificationRecord>>(emptyList())

    // app metadata caches (shared between service + UI threads)
    val appLabels = ConcurrentHashMap<String, String>()
    val appIcons = ConcurrentHashMap<String, ImageBitmap>()

    // ---------- load / save ----------
    private fun load() {
        botToken.value = prefs.getString("bot_token", "") ?: ""
        botUsername.value = prefs.getString("bot_username", "") ?: ""
        deviceName.value = prefs.getString("device_name", "") ?: ""
        themeMode.value = try {
            ThemeMode.valueOf(prefs.getString("theme", ThemeMode.AUTO.name) ?: ThemeMode.AUTO.name)
        } catch (e: Exception) { ThemeMode.AUTO }
        enabled.value = prefs.getBoolean("enabled", true)
        configured.value = prefs.getBoolean("configured", false)
        selectedApps.value = prefs.getStringSet("selected_apps", emptySet())?.toSet() ?: emptySet()
        mutedApps.value = prefs.getStringSet("muted_apps", emptySet())?.toSet() ?: emptySet()
        ownerChatId.value = prefs.getLong("owner_chat_id", -1L)
        pausedUntil.value = prefs.getLong("paused_until", 0L)
        includeTimestamp.value = prefs.getBoolean("include_timestamp", true)
        statsToday.value = prefs.getInt("stats_today", 0)
        statsTotal.value = prefs.getInt("stats_total", 0)
        recent.value = parseRecent(prefs.getString("recent", "[]") ?: "[]")
        rollDailyCounter()
    }

    private fun rollDailyCounter() {
        val today = todayKey()
        if ((prefs.getString("stats_date", "") ?: "") != today) {
            statsToday.value = 0
            prefs.edit().putString("stats_date", today).putInt("stats_today", 0).apply()
        }
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    fun setBotToken(token: String) {
        botToken.value = token.trim()
        prefs.edit().putString("bot_token", token.trim()).apply()
    }

    fun setBotUsername(name: String) {
        botUsername.value = name
        prefs.edit().putString("bot_username", name).apply()
    }

    fun setDeviceName(name: String) {
        deviceName.value = name.trim()
        prefs.edit().putString("device_name", name.trim()).apply()
    }

    fun setTheme(mode: ThemeMode) {
        themeMode.value = mode
        prefs.edit().putString("theme", mode.name).apply()
    }

    fun setEnabled(v: Boolean) {
        enabled.value = v
        prefs.edit().putBoolean("enabled", v).apply()
    }

    fun setIncludeTimestamp(v: Boolean) {
        includeTimestamp.value = v
        prefs.edit().putBoolean("include_timestamp", v).apply()
    }

    fun finishSetup() {
        configured.value = true
        prefs.edit().putBoolean("configured", true).apply()
    }

    fun toggleApp(pkg: String) {
        val s = selectedApps.value.toMutableSet()
        if (!s.add(pkg)) s.remove(pkg)
        selectedApps.value = s
        prefs.edit().putStringSet("selected_apps", s).apply()
    }

    fun setSelected(packages: Set<String>) {
        selectedApps.value = packages
        prefs.edit().putStringSet("selected_apps", packages).apply()
    }

    fun setOwnerChat(id: Long) {
        ownerChatId.value = id
        prefs.edit().putLong("owner_chat_id", id).apply()
    }

    fun pauseFor(ms: Long) {
        pausedUntil.value = if (ms <= 0) Long.MAX_VALUE else System.currentTimeMillis() + ms
        prefs.edit().putLong("paused_until", pausedUntil.value).apply()
    }

    fun resume() {
        pausedUntil.value = 0L
        prefs.edit().putLong("paused_until", 0L).apply()
    }

    fun pausedNow(): Boolean = pausedUntil.value > System.currentTimeMillis()

    fun isMuted(label: String): Boolean {
        val l = label.lowercase(Locale.US)
        return mutedApps.value.any { l.contains(it) || it.contains(l) }
    }

    fun setMuted(labels: Set<String>) {
        mutedApps.value = labels
        prefs.edit().putStringSet("muted_apps", labels).apply()
    }

    // ---------- bot command support ----------
    @Synchronized fun loadSeen(): Set<Long> = try {
        val arr = JSONArray(prefs.getString("seen_updates", "[]") ?: "[]")
        (0 until arr.length()).map { arr.getLong(it) }.toSet()
    } catch (e: Exception) { emptySet() }

    @Synchronized fun saveSeen(seen: Set<Long>) {
        val arr = JSONArray()
        seen.sorted().takeLast(400).forEach { arr.put(it) }
        prefs.edit().putString("seen_updates", arr.toString()).apply()
    }

    fun relayState(): RelayState = when {
        !configured.value || botToken.value.isBlank() -> RelayState.SETUP
        ownerChatId.value <= 0 -> RelayState.WAITING_LINK
        pausedNow() -> RelayState.PAUSED
        !enabled.value -> RelayState.OFF
        else -> RelayState.ACTIVE
    }

    // ---------- activity log ----------
    @Synchronized fun addRecent(r: NotificationRecord) {
        rollDailyCounter()
        val list = ArrayList(recent.value)
        list.add(0, r)
        if (list.size > 30) list.subList(30, list.size).clear()
        recent.value = list
        statsTotal.value += 1
        if (r.ok) statsToday.value += 1
        prefs.edit()
            .putString("recent", serializeRecent(list))
            .putInt("stats_today", statsToday.value)
            .putInt("stats_total", statsTotal.value)
            .apply()
    }

    @Synchronized fun clearRecent() {
        recent.value = emptyList()
        prefs.edit().putString("recent", "[]").apply()
    }

    private fun serializeRecent(list: List<NotificationRecord>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject()
                .put("a", it.appLabel)
                .put("p", it.packageName)
                .put("t", it.title)
                .put("x", it.text)
                .put("ts", it.time)
                .put("ok", it.ok))
        }
        return arr.toString()
    }

    private fun parseRecent(s: String): List<NotificationRecord> = try {
        val arr = JSONArray(s)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            NotificationRecord(
                appLabel = o.optString("a"), packageName = o.optString("p"),
                title = o.optString("t"), text = o.optString("x"),
                time = o.optLong("ts"), ok = o.optBoolean("ok", false)
            )
        }
    } catch (e: Exception) { emptyList() }

    // ---------- package metadata ----------
    fun labelFor(context: Context, pkg: String): String {
        appLabels[pkg]?.let { return it }
        return try {
            val pm = context.packageManager
            val label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            appLabels[pkg] = label
            label
        } catch (e: Exception) { pkg }
    }

    fun iconFor(context: Context, pkg: String): ImageBitmap? {
        appIcons[pkg]?.let { return it }
        return try {
            val d = context.packageManager.getApplicationIcon(pkg)
            val bmp = drawableToBitmap(d)
            val ib = bmp.asImageBitmap()
            appIcons[pkg] = ib
            ib
        } catch (e: Exception) { null }
    }

    private fun drawableToBitmap(d: Drawable, size: Int = 96): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        d.setBounds(0, 0, canvas.width, canvas.height)
        d.draw(canvas)
        return bmp
    }

    fun drawableToImageBitmap(d: Drawable, size: Int = 96): ImageBitmap =
        drawableToBitmap(d, size).asImageBitmap()
}

package com.telerelay.app.telegram

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Thin synchronous wrapper around the Telegram Bot API. Call from background threads. */
object TelegramClient {

    class TgException(val code: Int, message: String) : Exception(message)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Bot commands registered with Telegram (shown in the bot's menu). */
    val COMMANDS: List<Pair<String, String>> = listOf(
        "start" to "Link this chat as the owner",
        "help" to "Show everything the bot can do",
        "devices" to "Show all connected devices & status",
        "apps" to "Show apps this device forwards",
        "recent" to "Recent forwarded notifications",
        "search" to "Search forwarded notifications",
        "pause" to "Pause forwarding (optional minutes)",
        "resume" to "Resume forwarding",
        "mute" to "Stop forwarding an app",
        "unmute" to "Resume forwarding an app",
        "test" to "Send a test notification",
        "ping" to "Check whether the device is alive",
        "id" to "Show this chat's ID"
    )

    fun call(token: String, method: String, form: Map<String, String> = emptyMap()): JSONObject {
        val body = FormBody.Builder().apply {
            form.forEach { (k, v) -> add(k, v) }
        }.build()
        val req = Request.Builder()
            .url("https://api.telegram.org/bot$token/$method")
            .post(body)
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: "{}"
            val json = JSONObject(text)
            if (!json.optBoolean("ok", false)) {
                throw TgException(
                    json.optInt("error_code", resp.code),
                    json.optString("description", "Request failed (${resp.code})")
                )
            }
            return json
        }
    }

    /** Returns the bot's @username. Throws TgException on an invalid token. */
    fun getMe(token: String): String =
        call(token, "getMe").getJSONObject("result").optString("username")

    fun sendMessage(token: String, chatId: Long, html: String) {
        call(token, "sendMessage", mapOf(
            "chat_id" to chatId.toString(),
            "text" to html.take(4000),
            "parse_mode" to "HTML",
            "disable_web_page_preview" to "true"
        ))
    }

    fun setMyCommands(token: String) {
        val arr = JSONArray()
        COMMANDS.forEach { (cmd, desc) ->
            arr.put(JSONObject().put("command", cmd).put("description", desc))
        }
        call(token, "setMyCommands", mapOf("commands" to arr.toString()))
    }

    /**
     * Fetches updates without ever confirming an offset. Every device running
     * TeleRelay with this bot token therefore sees the same commands and can
     * respond for itself — nothing gets "stolen" from the other devices.
     */
    fun getUpdates(token: String): JSONArray =
        call(token, "getUpdates", mapOf(
            "limit" to "100",
            "allowed_updates" to "[\"message\"]"
        )).getJSONArray("result")

    fun deleteWebhook(token: String) {
        call(token, "deleteWebhook")
    }
}

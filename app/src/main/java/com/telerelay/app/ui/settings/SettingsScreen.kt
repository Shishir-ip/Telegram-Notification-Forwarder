package com.telerelay.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.telerelay.app.BuildConfig
import com.telerelay.app.data.Store
import com.telerelay.app.data.ThemeMode
import com.telerelay.app.telegram.Bot
import com.telerelay.app.telegram.TelegramClient
import com.telerelay.app.ui.components.IosSwitch
import com.telerelay.app.ui.components.RowDivider
import com.telerelay.app.ui.components.RowItem
import com.telerelay.app.ui.components.Segmented
import com.telerelay.app.ui.components.SectionHeader
import com.telerelay.app.ui.components.ScreenTitle
import com.telerelay.app.ui.components.TeleCard
import com.telerelay.app.ui.components.pressable
import com.telerelay.app.ui.components.rememberHaptics
import com.telerelay.app.ui.theme.LocalTele
import com.telerelay.app.ui.theme.TeleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen() {
    val C = LocalTele.current
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    var showTokenDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var actionStatus by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.bg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        ScreenTitle("Settings")

        SectionHeader("CONNECTION")
        TeleCard {
            RowItem(
                icon = Icons.Rounded.Key,
                title = "Bot token",
                subtitle = maskedToken(),
                onClick = { showTokenDialog = true }
            )
            RowDivider()
            RowItem(
                icon = Icons.Rounded.CheckCircle,
                title = "Bot status",
                subtitle = if (Store.botUsername.value.isNotBlank())
                    "Connected as @${Store.botUsername.value}" else "Not verified yet",
                iconTint = if (Store.botUsername.value.isNotBlank()) C.green else C.text3,
                iconBg = if (Store.botUsername.value.isNotBlank()) C.green.copy(alpha = 0.2f) else C.fill
            )
            RowDivider()
            RowItem(
                icon = Icons.Rounded.ChatBubbleOutline,
                title = "Linked chat",
                subtitle = if (Store.ownerChatId.value > 0)
                    "Chat ID ${Store.ownerChatId.value} — your private chat" else "Send /start to your bot in Telegram",
                iconTint = if (Store.ownerChatId.value > 0) C.green else C.orange,
                iconBg = if (Store.ownerChatId.value > 0) C.green.copy(alpha = 0.2f) else C.orange.copy(alpha = 0.2f)
            )
            RowDivider()
            RowItem(
                icon = Icons.Rounded.Send,
                title = "Send test notification",
                subtitle = "Check the full pipeline end to end",
                onClick = {
                    val token = Store.botToken.value
                    val chatId = Store.ownerChatId.value
                    if (token.isBlank() || chatId <= 0) {
                        actionStatus = "Add your bot token and send /start to the bot first."
                        haptics.reject()
                    } else {
                        actionStatus = "Sending…"
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    TelegramClient.sendMessage(
                                        token, chatId,
                                        Bot.testMessage(Store.deviceName.value, System.currentTimeMillis())
                                    )
                                }
                                actionStatus = "✓ Test sent — check your Telegram chat"
                                haptics.confirm()
                            } catch (e: Exception) {
                                actionStatus = "Test failed: ${e.message ?: "network error"}"
                                haptics.reject()
                            }
                        }
                    }
                }
            )
            RowDivider()
            RowItem(
                icon = Icons.Rounded.FormatListBulleted,
                title = "Register bot commands",
                subtitle = "Sync the /command menu with Telegram",
                onClick = {
                    val token = Store.botToken.value
                    if (token.isBlank()) {
                        actionStatus = "Add your bot token first."
                        haptics.reject()
                    } else {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { TelegramClient.setMyCommands(token) }
                                actionStatus = "✓ Command menu synced with your bot"
                                haptics.confirm()
                            } catch (e: Exception) {
                                actionStatus = "Sync failed: ${e.message ?: "network error"}"
                                haptics.reject()
                            }
                        }
                    }
                }
            )
        }
        AnimatedVisibility(
            visible = actionStatus != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                actionStatus ?: "",
                style = TeleType.footnote,
                color = C.text2,
                modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 18.dp)
            )
        }

        SectionHeader("DEVICE", modifier = Modifier.padding(top = 20.dp))
        TeleCard {
            RowItem(
                icon = Icons.Rounded.Smartphone,
                title = "Device name",
                subtitle = Store.deviceName.value.ifBlank { "Not set — shown on every message" },
                onClick = { showNameDialog = true }
            )
        }

        SectionHeader("APPEARANCE", modifier = Modifier.padding(top = 20.dp))
        TeleCard {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = C.accent,
                        modifier = Modifier.width(32.dp)
                    )
                    Text("Theme", style = TeleType.body, color = C.text)
                }
                Spacer(Modifier.height(12.dp))
                Segmented(
                    options = listOf(
                        "Light" to Icons.Rounded.LightMode,
                        "Dark" to Icons.Rounded.DarkMode,
                        "Auto" to Icons.Rounded.BrightnessAuto
                    ),
                    selected = Store.themeMode.value.ordinal,
                    onSelect = { Store.setTheme(ThemeMode.entries[it]) }
                )
            }
        }

        SectionHeader("MESSAGES", modifier = Modifier.padding(top = 20.dp))
        TeleCard {
            RowItem(
                icon = Icons.Rounded.Schedule,
                title = "Include timestamp",
                subtitle = "Append date & time to forwarded messages",
                trailing = {
                    IosSwitch(checked = Store.includeTimestamp.value) { Store.setIncludeTimestamp(it) }
                }
            )
            RowDivider()
            Column(Modifier.padding(16.dp)) {
                Text("Preview", style = TeleType.caption, color = C.text3)
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(C.fill)
                        .padding(12.dp)
                ) {
                    Text(
                        Bot.forwardMessage(
                            Store.deviceName.value.ifBlank { "Pixel 9" },
                            "WhatsApp",
                            "New message",
                            "Hey! Are we still meeting at 6?",
                            System.currentTimeMillis(),
                            Store.includeTimestamp.value
                        ),
                        style = TeleType.subhead,
                        color = C.text2
                    )
                }
            }
        }

        SectionHeader("BOT COMMANDS", modifier = Modifier.padding(top = 20.dp))
        TeleCard {
            Column(Modifier.padding(vertical = 6.dp)) {
                TelegramClient.COMMANDS.forEach { (cmd, desc) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "/$cmd",
                            style = TeleType.footnote,
                            color = C.accent,
                            modifier = Modifier.width(74.dp)
                        )
                        Text(
                            desc,
                            style = TeleType.footnote,
                            color = C.text2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        SectionHeader("ABOUT", modifier = Modifier.padding(top = 20.dp))
        TeleCard {
            RowItem(
                icon = Icons.Rounded.Info,
                title = "Version",
                subtitle = BuildConfig.VERSION_NAME,
                iconTint = C.text2,
                iconBg = C.fill
            )
            RowDivider()
            RowItem(
                icon = Icons.Rounded.Code,
                title = "GitHub repository",
                subtitle = "Shishir-ip/Telegram-Notification-Forwarder",
                onClick = {
                    try {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Shishir-ip/Telegram-Notification-Forwarder"))
                        )
                    } catch (_: Exception) {}
                }
            )
        }
        Spacer(Modifier.height(32.dp))
    }

    if (showTokenDialog) TokenDialog { showTokenDialog = false }
    if (showNameDialog) NameDialog { showNameDialog = false }
}

private fun maskedToken(): String {
    val t = Store.botToken.value
    return when {
        t.isBlank() -> "Not set — paste your @BotFather token"
        t.length > 14 -> t.take(8) + "••••••••" + t.takeLast(4)
        else -> "••••••"
    }
}

@Composable
private fun TokenDialog(onDismiss: () -> Unit) {
    val C = LocalTele.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(Store.botToken.value) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = C.card,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Bot token", style = TeleType.title3, color = C.text) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.trim(); error = null },
                    singleLine = true,
                    placeholder = { Text("123456789:AAE…", style = TeleType.subhead, color = C.text3) },
                    textStyle = TeleType.body.copy(color = C.text),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = C.accent,
                        unfocusedBorderColor = C.separator,
                        cursorColor = C.accent,
                        focusedContainerColor = C.bg,
                        unfocusedContainerColor = C.bg
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Create a bot with @BotFather in Telegram, then paste the token here. " +
                        "It will be verified before saving.",
                    style = TeleType.footnote,
                    color = C.text2
                )
                if (busy) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = C.accent, modifier = Modifier.width(16.dp).height(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Verifying token…", style = TeleType.footnote, color = C.text2)
                    }
                }
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = TeleType.footnote, color = C.red)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && text.isNotBlank(),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val username = withContext(Dispatchers.IO) { TelegramClient.getMe(text) }
                            withContext(Dispatchers.IO) { TelegramClient.setMyCommands(text) }
                            Store.setBotToken(text)
                            Store.setBotUsername(username)
                            haptics.confirm()
                            onDismiss()
                        } catch (e: Exception) {
                            haptics.reject()
                            error = if ((e.message ?: "").contains("401") || (e.message ?: "").contains("Unauthorized"))
                                "Invalid token — double-check it with @BotFather."
                            else (e.message ?: "Network error. Try again.")
                            busy = false
                        }
                    }
                }
            ) {
                Text("Save", color = if (text.isBlank()) C.text3 else C.accent, style = TeleType.body)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Cancel", color = C.text2, style = TeleType.body)
            }
        }
    )
}

@Composable
private fun NameDialog(onDismiss: () -> Unit) {
    val C = LocalTele.current
    val haptics = rememberHaptics()
    var text by remember { mutableStateOf(Store.deviceName.value.ifBlank { android.os.Build.MODEL }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.card,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Device name", style = TeleType.title3, color = C.text) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("e.g. Pixel 9 Pro", style = TeleType.subhead, color = C.text3) },
                    textStyle = TeleType.body.copy(color = C.text),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = C.accent,
                        unfocusedBorderColor = C.separator,
                        cursorColor = C.accent,
                        focusedContainerColor = C.bg,
                        unfocusedContainerColor = C.bg
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "This name prefixes every forwarded message, so with several phones " +
                        "you always know which one sent it.",
                    style = TeleType.footnote,
                    color = C.text2
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = {
                    Store.setDeviceName(text)
                    haptics.confirm()
                    onDismiss()
                }
            ) {
                Text("Save", color = if (text.isBlank()) C.text3 else C.accent, style = TeleType.body)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = C.text2, style = TeleType.body)
            }
        }
    )
}

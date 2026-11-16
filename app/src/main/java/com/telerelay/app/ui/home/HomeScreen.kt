package com.telerelay.app.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.telerelay.app.data.NotificationRecord
import com.telerelay.app.data.RelayState
import com.telerelay.app.data.Store
import com.telerelay.app.relay.RelayNotificationListener
import com.telerelay.app.ui.Tab
import com.telerelay.app.ui.components.AppIcon
import com.telerelay.app.ui.components.IosSwitch
import com.telerelay.app.ui.components.PulseDot
import com.telerelay.app.ui.components.RowDivider
import com.telerelay.app.ui.components.RowItem
import com.telerelay.app.ui.components.SectionHeader
import com.telerelay.app.ui.components.TeleCard
import com.telerelay.app.ui.components.pressable
import com.telerelay.app.ui.components.rememberHaptics
import com.telerelay.app.ui.theme.LocalTele
import com.telerelay.app.ui.theme.TeleType
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(onNavigate: (com.telerelay.app.ui.Tab) -> Unit) {
    val C = LocalTele.current
    val ctx = LocalContext.current
    val state = Store.relayState()
    val deviceName = Store.deviceName.value
    val botUser = Store.botUsername.value
    val ownerLinked = Store.ownerChatId.value > 0

    // watch notification-listener permission so the checklist updates live
    var listenerOk by remember { mutableStateOf(RelayNotificationListener.isEnabled(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1200)
            listenerOk = RelayNotificationListener.isEnabled(ctx)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.bg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(C.hero),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("TeleRelay", style = TeleType.largeTitle, color = C.text, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))

        HeroCard(state, deviceName, botUser)
        Spacer(Modifier.height(20.dp))

        // setup checklist
        val needsSetup = state == RelayState.SETUP || !ownerLinked || !listenerOk
        if (needsSetup) {
            SectionHeader("SET UP")
            TeleCard {
                if (Store.botToken.value.isBlank()) {
                    RowItem(
                        icon = Icons.Rounded.Bolt,
                        title = "Connect your Telegram bot",
                        subtitle = "Paste your @BotFather token in Settings",
                        onClick = { onNavigate(Tab.SETTINGS) }
                    )
                    RowDivider(insetStart = 0.dp)
                }
                if (deviceName.isBlank()) {
                    RowItem(
                        icon = Icons.Rounded.Smartphone,
                        title = "Name this device",
                        subtitle = "Shown on every forwarded message",
                        onClick = { onNavigate(Tab.SETTINGS) }
                    )
                    RowDivider(insetStart = 0.dp)
                }
                if (!listenerOk) {
                    RowItem(
                        icon = Icons.Rounded.Lock,
                        title = "Allow notification access",
                        subtitle = "Required to read notifications on this phone",
                        onClick = { RelayNotificationListener.openAccessSettings(ctx) }
                    )
                    RowDivider(insetStart = 0.dp)
                }
                if (!ownerLinked && Store.botToken.value.isNotBlank()) {
                    RowItem(
                        icon = Icons.Rounded.ChatBubbleOutline,
                        title = "Link your chat",
                        subtitle = "Send /start to your bot in Telegram, then come back",
                        onClick = { openTelegram(ctx) }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // forwarding master switch
        SectionHeader("FORWARDING")
        TeleCard {
            RowItem(
                icon = Icons.Rounded.NotificationsActive,
                title = "Forward notifications",
                subtitle = if (Store.enabled.value) "Selected apps relay to your bot" else "Relay is off on this device",
                trailing = { IosSwitch(checked = Store.enabled.value, onChange = { Store.setEnabled(it) }) }
            )
            if (Store.pausedNow()) {
                RowDivider()
                RowItem(
                    icon = Icons.Rounded.PauseCircle,
                    title = "Paused from Telegram",
                    subtitle = "Send /resume to your bot to resume",
                    titleColor = C.orange
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        // stats
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatChip("${Store.statsToday.value}", "Today", Modifier.weight(1f))
            StatChip("${Store.statsTotal.value}", "All time", Modifier.weight(1f))
            StatChip("${Store.selectedApps.value.size}", "Apps", Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))

        // recent activity
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("RECENT ACTIVITY", style = TeleType.caption, color = C.text3, modifier = Modifier.weight(1f))
            if (Store.recent.value.isNotEmpty()) {
                Text(
                    "Clear",
                    style = TeleType.subhead,
                    color = C.accent,
                    modifier = Modifier.pressable { Store.clearRecent() }
                )
            }
        }
        val recent = Store.recent.value
        if (recent.isEmpty()) {
            TeleCard {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Rounded.Devices,
                        contentDescription = null,
                        tint = C.text3,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Nothing forwarded yet", style = TeleType.callout, color = C.text2)
                    Text(
                        "Notifications you select will appear here",
                        style = TeleType.footnote,
                        color = C.text3
                    )
                }
            }
        } else {
            TeleCard {
                recent.take(6).forEachIndexed { i, r ->
                    if (i > 0) RowDivider()
                    RecentRow(r)
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun HeroCard(state: RelayState, deviceName: String, botUser: String) {
    val C = LocalTele.current
    val (label, sub, dot) = when (state) {
        RelayState.ACTIVE -> Triple("Active", "Relaying notifications in real time", C.green)
        RelayState.PAUSED -> Triple("Paused", "Forwarding is temporarily paused", C.orange)
        RelayState.OFF -> Triple("Forwarding off", "Turn on the switch below to resume", C.text3)
        RelayState.WAITING_LINK -> Triple("Chat not linked", "Send /start to your bot in Telegram", C.yellow)
        RelayState.SETUP -> Triple("Setup needed", "Complete the steps below to go live", C.red)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(158.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(C.hero)
            .pressable(scaleDown = 0.985f) { }
            .padding(20.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulseDot(dot, active = state == RelayState.ACTIVE)
                Spacer(Modifier.width(8.dp))
                AnimatedContent(
                    targetState = label,
                    transitionSpec = {
                        (slideInVertically(tween(260)) { it / 2 } + fadeIn(tween(260))) togetherWith
                            (slideOutVertically(tween(260)) { -it / 2 } + fadeOut(tween(260)))
                    },
                    label = "heroStatus"
                ) { l ->
                    Text(l, style = TeleType.headline, color = Color.White)
                }
                Spacer(Modifier.weight(1f))
                if (botUser.isNotBlank()) {
                    Text("@$botUser", style = TeleType.footnote, color = Color.White.copy(alpha = 0.65f))
                }
            }
            Spacer(Modifier.weight(1f))
            AnimatedContent(
                targetState = deviceName.ifBlank { "This device" },
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "heroDevice"
            ) { name ->
                Text(name, style = TeleType.title2, color = Color.White)
            }
            Text(sub, style = TeleType.footnote, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun StatChip(value: String, label: String, modifier: Modifier = Modifier) {
    val C = LocalTele.current
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(C.card)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = TeleType.title2, color = C.accent)
            Text(label, style = TeleType.caption, color = C.text2)
        }
    }
}

@Composable
private fun RecentRow(r: NotificationRecord) {
    val C = LocalTele.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(r.packageName, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    r.appLabel,
                    style = TeleType.subhead,
                    color = C.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (r.ok) C.green else C.red)
                )
            }
            if (r.title.isNotBlank() || r.text.isNotBlank()) {
                Text(
                    listOf(r.title, r.text).filter { it.isNotBlank() }.joinToString(" — ").take(90),
                    style = TeleType.footnote,
                    color = C.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(timeAgoLabel(r.time), style = TeleType.caption, color = C.text3)
    }
}

private fun timeAgoLabel(ms: Long): String {
    val diff = System.currentTimeMillis() - ms
    return when {
        diff < 60_000 -> "now"
        diff < 3_600_000 -> "${diff / 60_000}m"
        diff < 86_400_000 -> "${diff / 3_600_000}h"
        else -> "${diff / 86_400_000}d"
    }
}

private fun openTelegram(ctx: Context) {
    val launch = ctx.packageManager.getLaunchIntentForPackage("org.telegram.messenger")
    if (launch != null) {
        ctx.startActivity(launch)
    } else {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://telegram.org")))
        } catch (_: Exception) {}
    }
}

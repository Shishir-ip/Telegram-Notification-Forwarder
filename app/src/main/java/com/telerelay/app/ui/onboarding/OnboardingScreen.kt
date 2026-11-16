package com.telerelay.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.telerelay.app.data.Store
import com.telerelay.app.relay.RelayNotificationListener
import com.telerelay.app.telegram.TelegramClient
import com.telerelay.app.ui.components.pressable
import com.telerelay.app.ui.components.rememberHaptics
import com.telerelay.app.ui.theme.LocalTele
import com.telerelay.app.ui.theme.TeleType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OnboardingScreen() {
    val C = LocalTele.current
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 4 })

    var tokenText by remember { mutableStateOf(Store.botToken.value) }
    var botUser by remember { mutableStateOf(Store.botUsername.value) }
    var verifying by remember { mutableStateOf(false) }
    var verifyError by remember { mutableStateOf<String?>(null) }
    var nameText by remember { mutableStateOf(Store.deviceName.value.ifBlank { Build.MODEL }) }
    var granted by remember { mutableStateOf(RelayNotificationListener.isEnabled(ctx)) }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // live-check notification access while on the last page
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 3) {
            while (!RelayNotificationListener.isEnabled(ctx)) {
                delay(800)
                granted = RelayNotificationListener.isEnabled(ctx)
            }
            granted = true
        }
    }

    fun finish() {
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Store.setBotToken(tokenText)
        Store.setDeviceName(nameText)
        if (botUser.isNotBlank()) Store.setBotUsername(botUser)
        Store.setEnabled(true)
        Store.finishSetup()
        haptics.confirm()
    }

    fun next() {
        val page = pagerState.currentPage
        when (page) {
            0 -> scope.launch {
                pagerState.animateScrollToPage(1, animationSpec = spring(dampingRatio = 0.85f, stiffness = 220f))
            }
            1 -> {
                if (tokenText.isBlank()) { haptics.reject(); return }
                scope.launch {
                    pagerState.animateScrollToPage(2, animationSpec = spring(dampingRatio = 0.85f, stiffness = 220f))
                }
            }
            2 -> {
                if (nameText.isBlank()) { haptics.reject(); return }
                Store.setDeviceName(nameText)
                scope.launch {
                    pagerState.animateScrollToPage(3, animationSpec = spring(dampingRatio = 0.85f, stiffness = 220f))
                }
            }
            3 -> finish()
        }
        haptics.tick()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.bg)
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (pagerState.currentPage < 3) {
                Text(
                    "Skip",
                    style = TeleType.subhead,
                    color = C.text3,
                    modifier = Modifier.pressable { finish() }
                )
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> TokenPage(
                    token = tokenText,
                    onToken = { tokenText = it; verifyError = null; botUser = "" },
                    botUser = botUser,
                    verifying = verifying,
                    verifyError = verifyError,
                    onVerify = {
                        if (tokenText.isBlank()) {
                            verifyError = "Paste a token first."
                            haptics.reject()
                        } else {
                            verifying = true
                            verifyError = null
                            scope.launch {
                                try {
                                    val u = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        TelegramClient.getMe(tokenText)
                                    }
                                    botUser = u
                                    verifying = false
                                    haptics.confirm()
                                } catch (e: Exception) {
                                    verifying = false
                                    verifyError = if ((e.message ?: "").contains("401"))
                                        "Invalid token — double-check it with @BotFather."
                                    else (e.message ?: "Network error. Try again.")
                                    haptics.reject()
                                }
                            }
                        }
                    }
                )
                2 -> NamePage(name = nameText, onName = { nameText = it })
                3 -> AccessPage(granted = granted, onOpenSettings = {
                    haptics.select()
                    RelayNotificationListener.openAccessSettings(ctx)
                })
            }
        }

        // page dots
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(4) { i ->
                val sel = pagerState.currentPage == i
                val w by animateFloatAsState(if (sel) 22f else 8f, spring(dampingRatio = 0.8f), label = "dot")
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = w.dp, height = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (sel) C.accent else C.fill)
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // nav buttons
        Row(Modifier.fillMaxWidth()) {
            if (pagerState.currentPage > 0) {
                Box(
                    Modifier
                        .width(96.dp)
                        .height(54.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(C.fill)
                        .pressable { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Back", style = TeleType.headline, color = C.text2)
                }
                Spacer(Modifier.width(10.dp))
            }
            val enabled = when (pagerState.currentPage) {
                1 -> tokenText.isNotBlank()
                2 -> nameText.isNotBlank()
                else -> true
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(54.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(if (enabled) C.accent else C.fill)
                    .pressable(enabled = enabled) { next() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (pagerState.currentPage == 3) "Finish" else "Continue",
                    style = TeleType.headline,
                    color = if (enabled) Color.White else C.text3
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}

// ---------- pages ----------

@Composable
private fun WelcomePage() {
    val C = LocalTele.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(if (shown) 1f else 0.85f, spring(dampingRatio = 0.5f, stiffness = 160f), label = "logo")
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(500), label = "logoA")

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(96.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                .clip(RoundedCornerShape(26.dp))
                .background(C.hero),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("TeleRelay", style = TeleType.largeTitle, color = C.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Forward your phone's notifications\nto your own Telegram bot",
            style = TeleType.body,
            color = C.text2,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(30.dp))
        FeatureRow(Icons.Rounded.Bolt, "Real-time relay", "Every alert, delivered instantly")
        FeatureRow(Icons.Rounded.Devices, "Multi-device ready", "Device names keep chats organized")
        FeatureRow(Icons.Rounded.SmartToy, "Powerful bot commands", "Control everything from Telegram")
        FeatureRow(Icons.Rounded.Palette, "Light · dark · auto", "A polished, premium interface")
        Spacer(Modifier.weight(1.4f))
    }
}

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String) {
    val C = LocalTele.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(C.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = C.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = TeleType.headline, color = C.text)
            Text(sub, style = TeleType.footnote, color = C.text2)
        }
    }
}

@Composable
private fun TokenPage(
    token: String,
    onToken: (String) -> Unit,
    botUser: String,
    verifying: Boolean,
    verifyError: String?,
    onVerify: () -> Unit
) {
    val C = LocalTele.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(34.dp))
        Text("Connect your bot", style = TeleType.title2, color = C.text)
        Spacer(Modifier.height(10.dp))
        Text(
            "TeleRelay sends notifications through your personal Telegram bot. " +
                "Create one in seconds:",
            style = TeleType.body, color = C.text2
        )
        Spacer(Modifier.height(14.dp))
        listOf(
            "1.  Open Telegram and message @BotFather",
            "2.  Send /newbot and pick a name",
            "3.  Copy the token it gives you"
        ).forEach {
            Text(it, style = TeleType.subhead, color = C.text2, modifier = Modifier.padding(vertical = 4.dp))
        }
        Spacer(Modifier.height(20.dp))
        PastedField(value = token, onChange = onToken, placeholder = "123456789:AAE…")
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(C.fill)
                .pressable { onVerify() },
            contentAlignment = Alignment.Center
        ) {
            if (verifying) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = C.accent,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("Check token", style = TeleType.headline, color = C.accent)
            }
        }
        AnimatedVisibility(visible = botUser.isNotBlank(), enter = fadeIn(tween(300)), exit = fadeOut(tween(200))) {
            Row(
                Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = C.green, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Connected as @$botUser", style = TeleType.subhead, color = C.green)
            }
        }
        AnimatedVisibility(visible = verifyError != null, enter = fadeIn(tween(300)), exit = fadeOut(tween(200))) {
            Text(verifyError ?: "", style = TeleType.footnote, color = C.red, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun NamePage(name: String, onName: (String) -> Unit) {
    val C = LocalTele.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(34.dp))
        Text("Name this device", style = TeleType.title2, color = C.text)
        Spacer(Modifier.height(10.dp))
        Text(
            "Every forwarded message starts with this name. Installing TeleRelay on " +
                "several phones? Distinct names keep everything organized.",
            style = TeleType.body, color = C.text2
        )
        Spacer(Modifier.height(20.dp))
        PastedField(value = name, onChange = onName, placeholder = "e.g. Pixel 9 Pro")
        Spacer(Modifier.height(24.dp))
        // live message preview
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(C.card)
                    .padding(14.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Send, contentDescription = null, tint = C.accent, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("MESSAGE PREVIEW", style = TeleType.caption, color = C.text3)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        com.telerelay.app.telegram.Bot.forwardMessage(
                            name.ifBlank { "This device" },
                            "WhatsApp",
                            "New message",
                            "Hey! Are we still meeting at 6?",
                            System.currentTimeMillis(),
                            true
                        ),
                        style = TeleType.subhead,
                        color = C.text2
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessPage(granted: Boolean, onOpenSettings: () -> Unit) {
    val C = LocalTele.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(34.dp))
        Text("Allow notification access", style = TeleType.title2, color = C.text)
        Spacer(Modifier.height(10.dp))
        Text(
            "Android requires explicit permission for an app to read notifications. " +
                "Grant it in system settings, find TeleRelay in the list, and toggle it on.",
            style = TeleType.body, color = C.text2
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Notifications never leave your device except to your own private bot chat.",
            style = TeleType.footnote, color = C.text3
        )
        Spacer(Modifier.height(20.dp))
        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = C.green, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Access granted — you're all set!", style = TeleType.headline, color = C.green)
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(C.accentSoft)
                    .pressable { onOpenSettings() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = C.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open notification settings", style = TeleType.headline, color = C.accent)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "You can also grant this later — TeleRelay will remind you on the home screen.",
                style = TeleType.footnote, color = C.text3
            )
        }
    }
}

@Composable
private fun PastedField(value: String, onChange: (String) -> Unit, placeholder: String) {
    val C = LocalTele.current
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(placeholder, style = TeleType.subhead, color = C.text3) },
        textStyle = TeleType.body.copy(color = C.text),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.accent,
            unfocusedBorderColor = C.separator,
            cursorColor = C.accent,
            focusedContainerColor = C.card,
            unfocusedContainerColor = C.card
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

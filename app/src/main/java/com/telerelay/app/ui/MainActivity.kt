package com.telerelay.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.telerelay.app.data.Store
import com.telerelay.app.relay.CommandService
import com.telerelay.app.ui.apps.AppsScreen
import com.telerelay.app.ui.components.pressable
import com.telerelay.app.ui.components.rememberHaptics
import com.telerelay.app.ui.home.HomeScreen
import com.telerelay.app.ui.onboarding.OnboardingScreen
import com.telerelay.app.ui.settings.SettingsScreen
import com.telerelay.app.ui.theme.LocalTele
import com.telerelay.app.ui.theme.TeleTheme
import com.telerelay.app.ui.theme.TeleType

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        setContent {
            TeleTheme {
                TeleApp()
            }
        }
    }
}

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Relay", Icons.Rounded.Home),
    APPS("Apps", Icons.Rounded.Apps),
    SETTINGS("Settings", Icons.Rounded.Settings)
}

@Composable
fun TeleApp() {
    val ctx = LocalContext.current
    val configured = Store.configured.value
    val enabled = Store.enabled.value
    val token = Store.botToken.value

    // keep the command relay service in sync with user settings
    LaunchedEffect(configured, enabled, token) {
        if (configured && enabled && token.isNotBlank()) {
            CommandService.start(ctx)
        } else {
            CommandService.stop(ctx)
        }
    }

    AnimatedContent(
        targetState = !configured,
        transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(350)) },
        label = "rootSwitch"
    ) { onboarding ->
        if (onboarding) OnboardingScreen() else MainScaffold()
    }
}

@Composable
fun MainScaffold() {
    val C = LocalTele.current
    var tab by remember { mutableStateOf(Tab.HOME) }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.bg)
    ) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                    val spec = spring<androidx.compose.ui.unit.IntOffset>(
                        dampingRatio = 0.92f,
                        stiffness = 420f
                    )
                    (slideInHorizontally(spec) { dir * it / 3 } + fadeIn(tween(240, delayMillis = 50))) togetherWith
                        (slideOutHorizontally(spec) { -dir * it / 3 } + fadeOut(tween(170)))
                },
                label = "tabSwitch"
            ) { t ->
                when (t) {
                    Tab.HOME -> HomeScreen(onNavigate = { tab = it })
                    Tab.APPS -> AppsScreen()
                    Tab.SETTINGS -> SettingsScreen()
                }
            }
        }
        TeleTabBar(current = tab, onSelect = { tab = it })
    }
}

@Composable
fun TeleTabBar(current: Tab, onSelect: (Tab) -> Unit) {
    val C = LocalTele.current
    Column(
        Modifier
            .navigationBarsPadding()
            .fillMaxWidth()
            .background(C.tabBar)
    ) {
        HorizontalDivider(thickness = 0.8.dp, color = C.separator)
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Tab.entries.forEach { t ->
                val selected = t == current
                val scale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (selected) 1f else 0.94f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 480f),
                    label = "tabIconScale"
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressable(scaleDown = 0.9f) { onSelect(t) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        t.icon,
                        contentDescription = t.label,
                        tint = if (selected) C.accent else C.text3,
                        modifier = Modifier
                            .size(23.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                    )
                    Text(
                        t.label,
                        style = TeleType.caption,
                        color = if (selected) C.accent else C.text3
                    )
                }
            }
        }
    }
}

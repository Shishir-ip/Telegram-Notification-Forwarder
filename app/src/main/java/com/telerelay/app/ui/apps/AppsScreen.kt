package com.telerelay.app.ui.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.telerelay.app.data.AppEntry
import com.telerelay.app.data.Store
import com.telerelay.app.ui.components.AppIcon
import com.telerelay.app.ui.components.IosSwitch
import com.telerelay.app.ui.components.RowDivider
import com.telerelay.app.ui.components.ScreenTitle
import com.telerelay.app.ui.components.TeleSearchField
import com.telerelay.app.ui.components.pressable
import com.telerelay.app.ui.components.rememberHaptics
import com.telerelay.app.ui.theme.LocalTele
import com.telerelay.app.ui.theme.TeleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AppCategory(val label: String) {
    ALL("All"),
    USER("User"),
    SYSTEM("System"),
    FORWARDING("Forwarded")
}

@Composable
fun AppsScreen() {
    val C = LocalTele.current
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    var search by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var category by remember { mutableStateOf(AppCategory.USER) }
    val selected = Store.selectedApps.value

    LaunchedEffect(Unit) {
        apps = loadApps(ctx)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.bg)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        ScreenTitle("Apps", subtitle = "${selected.size} selected for forwarding")
        Spacer(Modifier.height(10.dp))
        TeleSearchField(search, { search = it }, "Search apps")
        Spacer(Modifier.height(12.dp))

        // category chips
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppCategory.entries.forEach { c ->
                CategoryChip(
                    label = c.label,
                    selected = c == category,
                    onSelect = { category = c }
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        val list = apps
        when {
            list == null -> {
                Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = C.accent, modifier = Modifier.size(30.dp))
                }
            }
            else -> {
                val filtered = filterApps(list, category, selected, search)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Select all",
                        style = TeleType.subhead,
                        color = C.accent,
                        modifier = Modifier.pressable {
                            Store.setSelected(selected + filtered.map { it.packageName }.toSet())
                            haptics.confirm()
                        }
                    )
                    Spacer(Modifier.width(18.dp))
                    Text(
                        "Clear",
                        style = TeleType.subhead,
                        color = C.accent,
                        modifier = Modifier.pressable {
                            Store.setSelected(selected - filtered.map { it.packageName }.toSet())
                            haptics.confirm()
                        }
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        when (category) {
                            AppCategory.FORWARDING -> "${filtered.size} forwarding"
                            else -> "${filtered.size} apps"
                        },
                        style = TeleType.caption,
                        color = C.text3
                    )
                }
                Spacer(Modifier.height(12.dp))

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(C.card)
                ) {
                    if (filtered.isEmpty()) {
                        Column(
                            Modifier.fillMaxSize().padding(bottom = 60.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val (title, sub) = if (category == AppCategory.FORWARDING)
                                "Nothing forwarded yet" to "Select apps to start relaying them"
                            else
                                "No apps match" to "Try a different search term"
                            Text(title, style = TeleType.callout, color = C.text2)
                            Spacer(Modifier.height(4.dp))
                            Text(sub, style = TeleType.footnote, color = C.text3)
                        }
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(filtered, key = { it.packageName }) { entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .pressable { Store.toggleApp(entry.packageName) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcon(entry.packageName, 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        entry.label,
                                        style = TeleType.body,
                                        color = C.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        entry.packageName,
                                        style = TeleType.caption,
                                        color = C.text3,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                IosSwitch(
                                    checked = entry.packageName in selected,
                                    onChange = { Store.toggleApp(entry.packageName) }
                                )
                            }
                            if (entry != filtered.last()) {
                                RowDivider(insetStart = 68.dp)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
    }
}

private fun filterApps(
    list: List<AppEntry>,
    category: AppCategory,
    selected: Set<String>,
    search: String
): List<AppEntry> {
    val byCategory = when (category) {
        AppCategory.ALL -> list
        AppCategory.USER -> list.filter { !it.isSystem }
        AppCategory.SYSTEM -> list.filter { it.isSystem }
        AppCategory.FORWARDING -> list.filter { it.packageName in selected }
    }
    if (search.isBlank()) return byCategory
    return byCategory.filter {
        it.label.contains(search, ignoreCase = true) ||
            it.packageName.contains(search, ignoreCase = true)
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onSelect: () -> Unit) {
    val C = LocalTele.current
    val haptics = rememberHaptics()
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) C.accentSoft else C.fill)
            .pressable { haptics.tick(); onSelect() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = TeleType.subhead,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) C.accent else C.text2
        )
    }
}

private suspend fun loadApps(ctx: Context): List<AppEntry> = withContext(Dispatchers.IO) {
    val pm = ctx.packageManager
    val out = ArrayList<AppEntry>()
    try {
        val self = ctx.packageName
        for (app in pm.getInstalledApplications(0)) {
            val pkg = app.packageName
            if (pkg == self) continue
            val label = try {
                pm.getApplicationLabel(app).toString()
            } catch (e: Exception) { pkg }
            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            Store.appLabels[pkg] = label
            out.add(AppEntry(pkg, label, isSystem))
        }
    } catch (_: Exception) {}
    out.sortedBy { it.label.lowercase() }
}

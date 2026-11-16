package com.telerelay.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.telerelay.app.data.Store
import com.telerelay.app.ui.theme.LocalTele
import com.telerelay.app.ui.theme.TeleType

// ---------- haptics ----------

class Haptics(private val view: View) {
    fun tick() { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    fun select() { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    fun confirm() {
        val c = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.VIRTUAL_KEY
        view.performHapticFeedback(c)
    }
    fun reject() { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

// ---------- press effect ----------

/** Springy scale-down on press + soft haptic on tap. Used everywhere for consistency. */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    scaleDown: Float = 0.965f,
    onClick: () -> Unit
): Modifier {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 550f),
        label = "pressScale"
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
            haptics.select()
            onClick()
        }
}

// ---------- primitives ----------

@Composable
fun TeleCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val C = LocalTele.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(C.card)
    ) { content() }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    val C = LocalTele.current
    Text(
        text,
        style = TeleType.caption,
        color = C.text3,
        modifier = modifier.padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 8.dp)
    )
}

@Composable
fun RowDivider(insetStart: Dp = 70.dp) {
    val C = LocalTele.current
    HorizontalDivider(
        thickness = 0.8.dp,
        color = C.separator,
        modifier = Modifier.padding(start = insetStart)
    )
}

@Composable
fun RowItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color? = null,
    iconBg: Color? = null,
    titleColor: Color? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val C = LocalTele.current
    val row = Modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.pressable { onClick() } else Modifier)
    Row(
        row.padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconBg ?: C.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint ?: C.accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = TeleType.body,
                color = titleColor ?: C.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Spacer(Modifier.height(1.dp))
                Text(
                    subtitle,
                    style = TeleType.footnote,
                    color = C.text2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        } else {
            Spacer(Modifier.width(6.dp))
            Chevron()
        }
    }
}

@Composable
fun Chevron() {
    val C = LocalTele.current
    Icon(
        Icons.Rounded.ChevronRight,
        contentDescription = null,
        tint = C.text3,
        modifier = Modifier.size(18.dp)
    )
}

// ---------- iOS switch ----------

@Composable
fun IosSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val C = LocalTele.current
    val haptics = rememberHaptics()
    Switch(
        checked = checked,
        onCheckedChange = {
            if (it) haptics.confirm() else haptics.select()
            onChange(it)
        },
        colors = SwitchDefaults.colors(
            checkedTrackColor = C.green,
            checkedThumbColor = Color.White,
            uncheckedTrackColor = C.fill,
            uncheckedThumbColor = Color(0xFFEBEBF5),
            uncheckedBorderColor = Color.Transparent,
            disabledCheckedTrackColor = C.green.copy(alpha = 0.5f),
            disabledUncheckedTrackColor = C.fill
        )
    )
}

// ---------- segmented control ----------

@Composable
fun Segmented(
    options: List<Pair<String, ImageVector?>>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    val C = LocalTele.current
    val haptics = rememberHaptics()
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(C.fill)
            .padding(2.dp)
    ) {
        val w = maxWidth / options.size
        val offset by animateDpAsState(
            targetValue = w * selected,
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 520f),
            label = "segOffset"
        )
        // sliding selection pill
        Box(
            Modifier
                .offset(x = offset)
                .width(w)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(C.card)
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, (label, icon) ->
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressable { haptics.tick(); onSelect(i) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (i == selected) C.text else C.text2,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        label,
                        style = TeleType.subhead,
                        fontWeight = FontWeight.SemiBold,
                        color = if (i == selected) C.text else C.text2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ---------- search field ----------

@Composable
fun TeleSearchField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val C = LocalTele.current
    Row(
        modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(C.fill)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = C.text3, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TeleType.body.copy(color = C.text),
            cursorBrush = SolidColor(C.accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = TeleType.body, color = C.text3, maxLines = 1)
                    }
                    inner()
                }
            }
        )
    }
}

// ---------- app icon ----------

@Composable
fun AppIcon(pkg: String, size: Dp = 40.dp) {
    val ctx = LocalContext.current
    val C = LocalTele.current
    val icon = remember(pkg) { Store.iconFor(ctx, pkg) }
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size)
        )
    } else {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(size / 4))
                .background(C.fill),
            contentAlignment = Alignment.Center
        ) {
            Text(
                pkg.take(1).uppercase(),
                style = TeleType.headline,
                color = C.text2
            )
        }
    }
}

// ---------- status dot ----------

@Composable
fun PulseDot(color: Color, active: Boolean, size: Dp = 10.dp) {
    val infinite = rememberInfiniteTransition(label = "pulse")
    val alpha by infinite.animateFloat(
        initialValue = if (active) 1f else 0.45f,
        targetValue = if (active) 0.35f else 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (active) 900 else 1, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

// ---------- misc ----------

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    val C = LocalTele.current
    Column(Modifier.padding(top = 10.dp, bottom = 14.dp)) {
        Text(title, style = TeleType.largeTitle, color = C.text)
        if (subtitle != null) {
            Text(subtitle, style = TeleType.subhead, color = C.text2)
        }
    }
}

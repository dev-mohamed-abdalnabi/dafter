package com.dafter.app

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ---------------- Theme ----------------
private val LightFallback = lightColorScheme(
    primary = Color(0xFF0B57D0), onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD), onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF565F71), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDAE2F9), onSecondaryContainer = Color(0xFF131C2B),
    tertiary = Color(0xFF146C2E), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC4EED0), onTertiaryContainer = Color(0xFF072711),
    background = Color(0xFFF8F9FF), onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF), onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC), onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFF74777F), outlineVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F3F9),
    surfaceContainer = Color(0xFFECEEF4), surfaceContainerHigh = Color(0xFFE6E8EE),
    surfaceContainerHighest = Color(0xFFE1E2E8),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002)
)

private val DarkFallback = darkColorScheme(
    primary = Color(0xFFA8C7FA), onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0), onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFBDC7DC), onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3F4759), onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFF6DD58C), onTertiary = Color(0xFF003915),
    tertiaryContainer = Color(0xFF0F5223), onTertiaryContainer = Color(0xFFC4EED0),
    background = Color(0xFF111318), onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318), onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E), onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099), outlineVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color(0xFF0C0E13), surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024), surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
)

private val T0 = Typography()
private val AppType = Typography(
    displaySmall = T0.displaySmall.copy(fontSize = 36.sp, lineHeight = 52.sp, fontWeight = FontWeight.ExtraBold),
    headlineMedium = T0.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
    headlineSmall = T0.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = T0.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = T0.titleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmall = T0.titleSmall.copy(fontWeight = FontWeight.Bold),
    labelLarge = T0.labelLarge.copy(fontWeight = FontWeight.Bold)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val cs = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else if (dark) DarkFallback else LightFallback
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = cs, typography = AppType, content = content)
    }
}

// ---------------- Icons ----------------
private class Part(val fill: Boolean, val w: Float, val b: PathBuilder.() -> Unit)

private fun outl(w: Float = 2f, b: PathBuilder.() -> Unit) = Part(false, w, b)
private fun solid(w: Float = 1.6f, b: PathBuilder.() -> Unit) = Part(true, w, b)
private fun vec(name: String, fill: Boolean = false, w: Float = 2f, mirror: Boolean = false, b: PathBuilder.() -> Unit): ImageVector =
    vecM(name, mirror, Part(fill, w, b))

private fun vecM(name: String, mirror: Boolean = false, vararg parts: Part): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = mirror).apply {
        parts.forEach { p ->
            path(
                fill = if (p.fill) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black), strokeLineWidth = p.w,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = p.b
            )
        }
    }.build()

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, true, true, cx + r, cy)
    arcTo(r, r, 0f, true, true, cx - r, cy)
}

private fun PathBuilder.rr(x: Float, y: Float, w: Float, h: Float, r: Float) {
    moveTo(x + r, y); horizontalLineTo(x + w - r)
    arcTo(r, r, 0f, false, true, x + w, y + r); verticalLineTo(y + h - r)
    arcTo(r, r, 0f, false, true, x + w - r, y + h); horizontalLineTo(x + r)
    arcTo(r, r, 0f, false, true, x, y + h - r); verticalLineTo(y + r)
    arcTo(r, r, 0f, false, true, x + r, y); close()
}

object Ic {
    val Notes by lazy { vecM("notes", false, outl { rr(5f, 3.5f, 14f, 17f, 3.8f) }, outl { moveTo(8.8f, 9f); horizontalLineTo(15.2f); moveTo(8.8f, 13f); horizontalLineTo(15.2f); moveTo(8.8f, 17f); horizontalLineTo(12.5f) }) }
    val Tasks by lazy { vecM("tasks", false, outl { circle(12f, 12f, 8.5f) }, outl(2.2f) { moveTo(8.2f, 12.3f); lineTo(11f, 15f); lineTo(16f, 9.2f) }) }
    val Settings by lazy { vec("settings") {
        moveTo(4f, 8f); horizontalLineTo(11.5f); circle(14f, 8f, 2.5f); moveTo(16.5f, 8f); horizontalLineTo(20f)
        moveTo(4f, 16f); horizontalLineTo(7f); circle(9.5f, 16f, 2.5f); moveTo(12f, 16f); horizontalLineTo(20f) } }
    val Search by lazy { vec("search", w = 2.2f) { circle(10.5f, 10.5f, 6.3f); moveTo(15.2f, 15.2f); lineTo(20f, 20f) } }
    val Close by lazy { vec("close", w = 2.2f) { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) } }
    val Check by lazy { vec("check", w = 2.4f) { moveTo(5f, 12.5f); lineTo(9.8f, 17.2f); lineTo(19f, 7.2f) } }
    val Menu by lazy { vec("menu", w = 2.2f) { moveTo(4f, 7f); horizontalLineTo(20f); moveTo(4f, 12f); horizontalLineTo(20f); moveTo(4f, 17f); horizontalLineTo(20f) } }
    val More by lazy { vec("more", w = 3.4f) { moveTo(12f, 5f); lineTo(12.01f, 5f); moveTo(12f, 12f); lineTo(12.01f, 12f); moveTo(12f, 19f); lineTo(12.01f, 19f) } }
    val Back by lazy { vec("back", w = 2.2f, mirror = true) { moveTo(9.5f, 5.5f); lineTo(16f, 12f); lineTo(9.5f, 18.5f) } }
    val Plus by lazy { vec("plus", w = 2.4f) { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) } }
    val Image by lazy { vecM("image", false, outl { rr(3.5f, 4.5f, 17f, 15f, 3.8f) }, solid(1.2f) { circle(9f, 10f, 1.4f) }, outl { moveTo(4f, 17f); lineTo(9f, 13.2f); lineTo(13f, 16.5f); lineTo(16f, 14f); lineTo(20f, 17.5f) }) }
    val Mic by lazy { vecM("mic", false, outl { rr(9f, 3.5f, 6f, 11f, 3f) }, outl { moveTo(5.5f, 11.5f); arcTo(6.5f, 6.5f, 0f, false, false, 18.5f, 11.5f); moveTo(12f, 18f); verticalLineTo(20.5f) }) }
    val Draw by lazy { vec("draw") { moveTo(5f, 19f); lineTo(5.6f, 15.4f); lineTo(15.8f, 5.2f); lineTo(18.8f, 8.2f); lineTo(8.6f, 18.4f); close(); moveTo(13.8f, 7.2f); lineTo(16.8f, 10.2f) } }
    val Trash by lazy { vec("trash") {
        moveTo(4.5f, 7f); horizontalLineTo(19.5f); moveTo(9.5f, 7f); verticalLineTo(4.5f); horizontalLineTo(14.5f); verticalLineTo(7f)
        moveTo(6.5f, 7f); lineTo(7.4f, 18f); arcTo(1.6f, 1.6f, 0f, false, false, 9f, 19.5f); horizontalLineTo(15f)
        arcTo(1.6f, 1.6f, 0f, false, false, 16.6f, 18f); lineTo(17.5f, 7f) } }
    val Folder by lazy { vec("folder") {
        moveTo(3.5f, 7.5f); arcTo(2f, 2f, 0f, false, true, 5.5f, 5.5f); horizontalLineTo(9.5f); lineTo(11.5f, 8f); horizontalLineTo(18.5f)
        arcTo(2f, 2f, 0f, false, true, 20.5f, 10f); verticalLineTo(17.5f); arcTo(2f, 2f, 0f, false, true, 18.5f, 19.5f)
        horizontalLineTo(5.5f); arcTo(2f, 2f, 0f, false, true, 3.5f, 17.5f); close() } }
    val Play by lazy { vec("play", fill = true, w = 3f) { moveTo(8.7f, 6.7f); lineTo(17.4f, 12f); lineTo(8.7f, 17.3f); close() } }
    val Pause by lazy { vecM("pause", false, solid(2.4f) { rr(7.4f, 6.2f, 2.2f, 11.6f, 1.1f); rr(14.4f, 6.2f, 2.2f, 11.6f, 1.1f) }) }
    val Stop by lazy { vecM("stop", false, solid(2f) { rr(7f, 7f, 10f, 10f, 2.5f) }) }
    val Star by lazy { vec("star") { moveTo(12f, 3.8f); lineTo(14.6f, 9.2f); lineTo(20.5f, 10f); lineTo(16.2f, 14.1f); lineTo(17.3f, 20f); lineTo(12f, 17.2f); lineTo(6.7f, 20f); lineTo(7.8f, 14.1f); lineTo(3.5f, 10f); lineTo(9.4f, 9.2f); close() } }
    val Pin by lazy { vec("pin") { moveTo(9f, 4.5f); lineTo(15f, 4.5f); lineTo(14.2f, 10f); lineTo(17f, 13.5f); lineTo(7f, 13.5f); lineTo(9.8f, 10f); close(); moveTo(12f, 13.5f); lineTo(12f, 20f) } }
    val Lock by lazy { vecM("lock", false, outl { rr(5.5f, 10.5f, 13f, 9.5f, 3f) }, outl { moveTo(8.5f, 10.5f); verticalLineTo(8f); arcTo(3.5f, 3.5f, 0f, false, true, 15.5f, 8f); verticalLineTo(10.5f) }) }
    val Undo by lazy { vec("undo", w = 2.2f) { moveTo(9f, 6f); lineTo(5f, 10.5f); lineTo(9f, 15f); moveTo(5f, 10.5f); horizontalLineTo(14f); arcTo(4.5f, 4.5f, 0f, false, true, 14f, 19.5f); horizontalLineTo(9f) } }
    val Clock by lazy { vec("clock") { circle(12f, 12f, 8.5f); moveTo(12f, 7.5f); verticalLineTo(12f); lineTo(15f, 14f) } }
}

// ---------------- Cookie ----------------
class CookieShape(private val lobes: Int = 9, private val amp: Float = 0.06f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = min(size.width, size.height) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val p = Path()
        val steps = 180
        for (i in 0..steps) {
            val a = (2.0 * PI * i / steps).toFloat()
            val rad = r * (1f - amp + amp * cos(lobes * a))
            val x = cx + rad * cos(a)
            val y = cy + rad * sin(a)
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return Outline.Generic(p)
    }
}

@Composable
fun CookieIcon(
    icon: ImageVector, size: Dp, container: Color, tint: Color, modifier: Modifier = Modifier,
    spin: Boolean = true, desc: String? = null, onClick: (() -> Unit)? = null
) {
    val shape = remember { CookieShape() }
    val rot by rememberInfiniteTransition(label = "cookie").animateFloat(
        0f, 360f, infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "rot"
    )
    val click = if (onClick != null) Modifier.clip(CircleShape).clickable(onClick = onClick) else Modifier
    Box(modifier.size(size).then(click), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = if (spin) rot else 0f }.clip(shape).background(container))
        Icon(icon, desc, Modifier.size(size * 0.44f), tint = tint)
    }
}

@Composable
fun Modifier.glow(color: Color, alpha: Float): Modifier = drawBehind {
    drawRect(
        Brush.radialGradient(
            listOf(color.copy(alpha = alpha), Color.Transparent),
            center = Offset(size.width * 0.12f, size.height * 0.04f), radius = size.width * 0.95f
        )
    )
}

// ---------------- Components ----------------
class NavTab(val label: String, val icon: ImageVector)

@Composable
fun FloatingNav(items: List<NavTab>, sel: Int, on: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val hap = LocalHapticFeedback.current
    Surface(shape = CircleShape, color = cs.surfaceContainerHigh, shadowElevation = 10.dp) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEachIndexed { i, t ->
                val a = i == sel
                val bg by animateColorAsState(if (a) cs.primaryContainer else Color.Transparent, label = "nb")
                Row(
                    Modifier.height(54.dp).clip(CircleShape).background(bg)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            hap.performHapticFeedback(HapticFeedbackType.TextHandleMove); on(i)
                        }
                        .padding(horizontal = 20.dp).animateContentSize(spring(0.8f, 400f)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(t.icon, t.label, tint = if (a) cs.onPrimaryContainer else cs.onSurfaceVariant)
                    AnimatedVisibility(a, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                        Text(t.label, Modifier.padding(start = 10.dp), color = cs.onPrimaryContainer, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun Switcher(opts: List<String>, sel: Int, modifier: Modifier = Modifier, on: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.fillMaxWidth().height(54.dp).clip(CircleShape).background(cs.surfaceContainerHigh).padding(4.dp)) {
        val w = maxWidth / opts.size
        val x by animateDpAsState(w * sel.coerceAtLeast(0), spring(0.8f, 420f), label = "sw")
        Box(Modifier.align(Alignment.CenterStart).offset(x = x).width(w).fillMaxHeight().clip(CircleShape).background(cs.primary))
        Row(Modifier.fillMaxSize()) {
            opts.forEachIndexed { i, o ->
                val c by animateColorAsState(if (i == sel) cs.onPrimary else cs.onSurface, label = "swc")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { on(i) },
                    contentAlignment = Alignment.Center
                ) { Text(o, color = c, style = MaterialTheme.typography.titleSmall, maxLines = 1) }
            }
        }
    }
}

@Composable
fun PressFab(icon: ImageVector, desc: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pressed) 20.dp else 32.dp, spring(0.55f, 600f), label = "r")
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, spring(0.55f, 600f), label = "s")
    Box(
        Modifier.size(64.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(RoundedCornerShape(r)).background(cs.primary)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, Modifier.size(28.dp), tint = cs.onPrimary) }
}

@Composable
fun PillButton(text: String, modifier: Modifier = Modifier, tonal: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pressed) 18.dp else 28.dp, spring(0.55f, 600f), label = "r")
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, spring(0.55f, 600f), label = "s")
    Box(
        modifier.height(56.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(RoundedCornerShape(r))
            .background(if (tonal) cs.secondaryContainer else cs.primary)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, style = MaterialTheme.typography.titleMedium, color = if (tonal) cs.onSecondaryContainer else cs.onPrimary) }
}

@Composable
fun IconBtn(icon: ImageVector, desc: String, tint: Color = MaterialTheme.colorScheme.onSurface, bg: Color = Color.Transparent, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, Modifier.size(24.dp), tint = tint)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FChip(text: String, sel: Boolean, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (sel) cs.primaryContainer else cs.surfaceContainerHigh, label = "chip")
    Box(
        Modifier.height(44.dp).clip(CircleShape).background(bg).combinedClickable(onLongClick = onLong, onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, style = MaterialTheme.typography.titleSmall, color = if (sel) cs.onPrimaryContainer else cs.onSurfaceVariant, maxLines = 1) }
}

@Composable
fun fieldColors(): TextFieldColors {
    val c = MaterialTheme.colorScheme.surfaceContainerHigh
    return TextFieldDefaults.colors(
        focusedContainerColor = c, unfocusedContainerColor = c, disabledContainerColor = c,
        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent
    )
}

@Composable
fun clearFieldColors(): TextFieldColors = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent
)

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CookieIcon(icon, 128.dp, cs.primaryContainer, cs.onPrimaryContainer)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionTitle(text: String, first: Boolean = false) {
    Text(text, Modifier.padding(top = if (first) 0.dp else 28.dp, bottom = 6.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun SettingRow(icon: ImageVector, title: String, sub: String, onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    val m = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).then(m).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = cs.onSecondaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        if (trailing != null) trailing()
    }
}

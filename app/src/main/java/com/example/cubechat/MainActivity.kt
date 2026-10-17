package com.example.cubechat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cubechat.net.ChatLine
import com.example.cubechat.net.CommandInfo
import com.example.cubechat.net.Hud
import com.example.cubechat.net.ItemView
import com.example.cubechat.net.Key
import com.example.cubechat.net.MenuView
import kotlin.math.abs

// ======================================================================= Tema

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5DBB7E), onPrimary = Color(0xFF06210F),
    primaryContainer = Color(0xFF1C3827), onPrimaryContainer = Color(0xFFBFE9CC),
    secondary = Color(0xFF9FB3C8), onSecondary = Color(0xFF0E1A26),
    secondaryContainer = Color(0xFF263241), onSecondaryContainer = Color(0xFFD8E4F1),
    tertiary = Color(0xFFE0B25C), onTertiary = Color(0xFF2A1D00),
    error = Color(0xFFF2766B), onError = Color(0xFF3A0905),
    background = Color(0xFF0E1114), onBackground = Color(0xFFE6E8EB),
    surface = Color(0xFF0E1114), onSurface = Color(0xFFE6E8EB),
    surfaceVariant = Color(0xFF1D2229), onSurfaceVariant = Color(0xFF9AA3AD),
    outline = Color(0xFF3A424C), outlineVariant = Color(0xFF272D35),
    surfaceContainerLowest = Color(0xFF0A0C0F), surfaceContainerLow = Color(0xFF12161A),
    surfaceContainer = Color(0xFF171B20), surfaceContainerHigh = Color(0xFF1E232A),
    surfaceContainerHighest = Color(0xFF262C34)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E7A47), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFEBD9), onPrimaryContainer = Color(0xFF0B2B18),
    secondary = Color(0xFF52606F), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE5EE), onSecondaryContainer = Color(0xFF14202C),
    tertiary = Color(0xFF8A6200), onTertiary = Color(0xFFFFFFFF),
    error = Color(0xFFBA3B30), onError = Color(0xFFFFFFFF),
    background = Color(0xFFF5F6F8), onBackground = Color(0xFF14181D),
    surface = Color(0xFFF5F6F8), onSurface = Color(0xFF14181D),
    surfaceVariant = Color(0xFFE6EAEE), onSurfaceVariant = Color(0xFF59636E),
    outline = Color(0xFF9AA4AF), outlineVariant = Color(0xFFD5DAE0),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF0F2F5),
    surfaceContainer = Color(0xFFEBEEF2), surfaceContainerHigh = Color(0xFFE5E9ED),
    surfaceContainerHighest = Color(0xFFDFE3E8)
)

@Composable
fun CubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

@Composable
private fun isDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

// ======================================================================= Activity & akar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CubeTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { App() }
            }
        }
    }
}

@Composable
fun App(app: AppViewModel = viewModel()) {
    // Tombol kembali dari sebuah sesi menuju Beranda (sesi tetap tersambung).
    BackHandler(enabled = app.current != null) { app.goHome() }
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        // Di tablet / layar lebar isi dibatasi lebarnya supaya tetap nyaman dibaca.
        Box(Modifier.widthIn(max = 640.dp).fillMaxWidth().fillMaxHeight()) {
            val cur = app.current
            if (cur == null) HomeScreen(app) else key(cur.id) { MainScreen(app, cur) }
        }
    }
    AppDialogs(app)
}

// ======================================================================= Ikon (digambar sendiri, tanpa emoji)

enum class Glyph { Chat, Controls, Bag, Info, Send, Up, Down, Left, Right, Jump, Close }

@Composable
fun GlyphIcon(glyph: Glyph, modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val st = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(tint, p(x1, y1), p(x2, y2), strokeWidth = 2f * u, cap = StrokeCap.Round)
        }
        fun poly(vararg xy: Float) {
            val path = Path()
            path.moveTo(xy[0] * u, xy[1] * u)
            var i = 2
            while (i < xy.size) {
                path.lineTo(xy[i] * u, xy[i + 1] * u)
                i += 2
            }
            drawPath(path, tint, style = st)
        }
        when (glyph) {
            Glyph.Chat -> {
                drawRoundRect(tint, p(3f, 4f), Size(18f * u, 13f * u), cornerRadius = CornerRadius(3f * u), style = st)
                poly(8f, 17f, 8f, 21f, 12.5f, 17f)
            }
            Glyph.Controls -> {
                drawRoundRect(tint, p(2.5f, 7f), Size(19f * u, 11f * u), cornerRadius = CornerRadius(5f * u), style = st)
                line(7f, 10.2f, 7f, 14.8f)
                line(4.7f, 12.5f, 9.3f, 12.5f)
                drawCircle(tint, radius = 1.2f * u, center = p(15.5f, 11.3f))
                drawCircle(tint, radius = 1.2f * u, center = p(18f, 13.7f))
            }
            Glyph.Bag -> {
                drawRoundRect(tint, p(5f, 9f), Size(14f * u, 12f * u), cornerRadius = CornerRadius(3f * u), style = st)
                drawArc(
                    tint, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = p(8.5f, 3.5f), size = Size(7f * u, 7f * u), style = st
                )
                line(5f, 14.5f, 19f, 14.5f)
            }
            Glyph.Info -> {
                drawCircle(tint, radius = 9f * u, center = p(12f, 12f), style = st)
                drawCircle(tint, radius = 1.25f * u, center = p(12f, 7.6f))
                line(12f, 11f, 12f, 16.5f)
            }
            Glyph.Send -> {
                poly(4f, 11.5f, 20f, 4f, 13.5f, 20f, 11f, 13f, 4f, 11.5f)
                line(11f, 13f, 20f, 4f)
            }
            Glyph.Up -> poly(6f, 15f, 12f, 9f, 18f, 15f)
            Glyph.Down -> poly(6f, 9f, 12f, 15f, 18f, 9f)
            Glyph.Left -> poly(15f, 6f, 9f, 12f, 15f, 18f)
            Glyph.Right -> poly(9f, 6f, 15f, 12f, 9f, 18f)
            Glyph.Jump -> {
                poly(6f, 12f, 12f, 6f, 18f, 12f)
                poly(6f, 18f, 12f, 12f, 18f, 18f)
            }
            Glyph.Close -> {
                line(6f, 6f, 18f, 18f)
                line(18f, 6f, 6f, 18f)
            }
        }
    }
}

// ======================================================================= Komponen dasar

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun AppField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    helper: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = cs.onSurface, fontSize = 16.sp),
            cursorBrush = SolidColor(cs.primary),
            keyboardOptions = keyboardOptions,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(cs.surfaceContainerHigh)
                        .border(1.dp, cs.outlineVariant, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = cs.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 16.sp)
                    }
                    inner()
                }
            }
        )
        if (helper != null) {
            Text(helper, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

/** Tombol kecil bergaya "chip" yang jelas aktif / tidak aktif. */
@Composable
fun ToggleChip(label: String, on: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .height(42.dp)
            .clip(shape)
            .background(if (on) cs.primaryContainer else cs.surfaceContainerHigh)
            .border(1.dp, if (on) cs.primary else cs.outlineVariant, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (on) cs.onPrimaryContainer else cs.onSurface,
            maxLines = 1
        )
    }
}

@Composable
fun SmallButton(label: String, modifier: Modifier = Modifier, strong: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .height(42.dp)
            .clip(shape)
            .background(if (strong) cs.primary else cs.surfaceContainerHigh)
            .then(if (strong) Modifier else Modifier.border(1.dp, cs.outlineVariant, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (strong) cs.onPrimary else cs.onSurface,
            maxLines = 1
        )
    }
}

/** Tombol yang aktif selama ditahan (untuk berjalan dan melompat). */
@Composable
fun HoldButton(
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onHold: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val base = if (accent) cs.primaryContainer else cs.surfaceContainerHigh
    val bg by animateColorAsState(if (pressed) cs.primary else base, label = "hold")
    val fg = if (pressed) cs.onPrimary else if (accent) cs.onPrimaryContainer else cs.onSurface
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, cs.outlineVariant, shape)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onHold(true)
                    try {
                        tryAwaitRelease()
                    } finally {
                        pressed = false
                        onHold(false)
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) { content() }
    }
}

/** Ubah pesan teknis menjadi kalimat yang gampang dipahami. Null kalau tidak ada masalah. */
private fun problemOf(raw: String): Pair<String, String>? {
    if (raw == "Belum terhubung" || raw == "Terputus" || raw.startsWith("Menghubungkan") || raw.startsWith("Masuk server")) {
        return null
    }
    val friendly = when {
        raw.contains("UnknownHost", true) ->
            "Alamat server tidak ditemukan. Periksa penulisannya dan pastikan internet menyala."
        raw.contains("refused", true) || raw.contains("ConnectException", true) ->
            "Server menolak koneksi. Server mungkin sedang mati atau port-nya salah."
        raw.contains("timed out", true) || raw.contains("timeout", true) ->
            "Server tidak merespons. Coba lagi sebentar lagi."
        raw.contains("online-mode", true) || raw.contains("Microsoft", true) ->
            "Server ini mewajibkan akun Microsoft. Versi ini baru bisa masuk ke server mode offline."
        raw.startsWith("Gagal") -> "Tidak bisa terhubung ke server."
        raw.startsWith("Terputus") -> "Koneksi ke server terputus."
        else -> raw
    }
    return friendly to (if (friendly == raw) "" else raw)
}

// ======================================================================= Layar utama

@Composable
fun MainScreen(app: AppViewModel, vm: GameSession) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var askExit by remember { mutableStateOf(false) }
    val hud = vm.hud

    Column(Modifier.fillMaxSize()) {
        SessionBar(app, vm)
        TopBar(vm, hud) { askExit = true }
        if (!vm.alive) EndedBanner(app, vm)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> ChatTab(vm)
                1 -> ControlTab(vm)
                2 -> BagTab(vm)
                else -> InfoTab(vm) { name ->
                    vm.chatInput = "/$name "
                    tab = 0
                }
            }
            Overlay(vm, hud)
        }
        BottomNav(tab) { tab = it }
    }

    val menu = hud?.menu
    if (menu != null) MenuDialog(vm, menu)

    if (askExit) {
        AlertDialog(
            onDismissRequest = { askExit = false },
            title = { Text("Keluar dari server?") },
            text = { Text("Koneksi akun ini akan diputus. Sesi lain tetap berjalan. Untuk meninggalkan sesi tanpa memutus, pakai tombol Beranda.") },
            confirmButton = {
                TextButton(onClick = {
                    askExit = false
                    app.closeSession(vm)
                }) { Text("Keluar") }
            },
            dismissButton = { TextButton(onClick = { askExit = false }) { Text("Batal") } }
        )
    }
}

/** Muncul di atas isi sesi kalau koneksi terputus: tawarkan sambung ulang. */
@Composable
fun EndedBanner(app: AppViewModel, vm: GameSession) {
    val cs = MaterialTheme.colorScheme
    val problem = problemOf(vm.status)
    Column(
        Modifier.fillMaxWidth().background(cs.error.copy(alpha = 0.12f)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(problem?.first ?: "Koneksi terputus.", color = cs.error, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        if (problem != null && problem.second.isNotBlank()) {
            Text(problem.second, fontSize = 12.sp, color = cs.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("Sambung ulang", Modifier.weight(1f), strong = true) { app.reconnect(vm) }
            SmallButton("Tutup sesi", Modifier.weight(1f)) { app.closeSession(vm) }
        }
    }
}

@Composable
fun TopBar(vm: GameSession, hud: Hud?, onExit: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ready = hud != null && hud.spawned
    Column(
        Modifier.fillMaxWidth().background(cs.surfaceContainer).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    vm.server.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dot = if (!vm.alive) cs.error else if (ready) cs.primary else cs.tertiary
                    Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                    Spacer(Modifier.width(6.dp))
                    val text = when {
                        !vm.alive -> vm.username + "  \u2022  Terputus"
                        hud == null -> vm.username + "  \u2022  Menghubungkan..."
                        !hud.spawned -> vm.username + "  \u2022  Memuat dunia..."
                        else -> vm.username + "  \u2022  " + modeLabel(hud.gameMode)
                    }
                    Text(text, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }
            TextButton(onClick = onExit) { Text("Keluar", color = cs.error) }
        }
        if (hud != null && hud.spawned) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatBar("Nyawa", hud.health, 20f, cs.error, Modifier.weight(1f))
                StatBar("Lapar", hud.food.toFloat(), 20f, cs.tertiary, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun StatBar(label: String, value: Float, max: Float, color: Color, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("${value.toInt()}/${max.toInt()}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(cs.surfaceContainerHighest)) {
            Box(Modifier.fillMaxWidth((value / max).coerceIn(0f, 1f)).fillMaxHeight().background(color))
        }
    }
}

@Composable
fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(cs.surfaceContainer)) {
        HorizontalDivider(color = cs.outlineVariant)
        Row(Modifier.fillMaxWidth()) {
            NavItem(Glyph.Chat, "Obrolan", selected == 0, Modifier.weight(1f)) { onSelect(0) }
            NavItem(Glyph.Controls, "Kontrol", selected == 1, Modifier.weight(1f)) { onSelect(1) }
            NavItem(Glyph.Bag, "Tas", selected == 2, Modifier.weight(1f)) { onSelect(2) }
            NavItem(Glyph.Info, "Info", selected == 3, Modifier.weight(1f)) { onSelect(3) }
        }
    }
}

@Composable
fun NavItem(glyph: Glyph, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.clickable(onClick = onClick).padding(top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(16.dp))
                .background(if (selected) cs.secondaryContainer else Color.Transparent)
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            GlyphIcon(glyph, Modifier.size(22.dp), tint = if (selected) cs.onSecondaryContainer else cs.onSurfaceVariant)
        }
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) cs.onSurface else cs.onSurfaceVariant
        )
    }
}

@Composable
fun Overlay(vm: GameSession, hud: Hud?) {
    if (hud == null) return
    val scrim = Color.Black.copy(alpha = 0.7f)
    Box(Modifier.fillMaxSize()) {
        if (hud.title != null || hud.subtitle != null) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp)
                    .clip(RoundedCornerShape(16.dp)).background(scrim).padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                hud.title?.let {
                    Text(
                        toAnnotated(it, true), fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, color = Color.White
                    )
                }
                hud.subtitle?.let {
                    Text(toAnnotated(it, true), fontSize = 15.sp, textAlign = TextAlign.Center, color = Color.White)
                }
            }
        }
        hud.actionBar?.let {
            Text(
                toAnnotated(it, true),
                fontSize = 14.sp,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(10.dp)).background(scrim).padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
        if (hud.dead) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp)
                    .clip(RoundedCornerShape(16.dp)).background(scrim).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Kamu mati", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                SmallButton("Hidup lagi", strong = true) { vm.respawn() }
            }
        }
    }
}

// ======================================================================= Tab Obrolan

@Composable
fun ChatTab(vm: GameSession) {
    val cs = MaterialTheme.colorScheme
    val dark = isDark()
    val listState = rememberLazyListState()
    val cmds = vm.hud?.commands ?: emptyList()
    val input = vm.chatInput

    LaunchedEffect(vm.lines.size) {
        if (vm.lines.isNotEmpty()) listState.animateScrollToItem(vm.lines.size - 1)
    }

    fun submit() {
        if (vm.chatInput.isNotBlank()) {
            vm.send(vm.chatInput)
            vm.chatInput = ""
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (vm.lines.isEmpty()) {
                Text(
                    "Belum ada pesan.\nPesan dari server dan pemain lain akan muncul di sini.",
                    Modifier.align(Alignment.Center).padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(vm.lines) { line ->
                    Text(toAnnotated(line, dark), fontSize = 15.sp, lineHeight = 21.sp)
                }
            }
        }

        val suggestions = suggestionsFor(input, cmds)
        if (suggestions.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(suggestions) { c -> SmallButton("/" + c.name) { vm.chatInput = "/" + c.name + " " } }
            }
        } else if (input.isEmpty() && vm.recentSent.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vm.recentSent.take(3)) { m ->
                    SmallButton(if (m.length > 24) m.take(24) + "..." else m) { vm.chatInput = m }
                }
            }
        }
        val hint = usageHint(input, cmds)
        if (hint != null) {
            Text(
                hint,
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                fontSize = 12.sp,
                color = cs.primary
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicTextField(
                value = input,
                onValueChange = { vm.chatInput = it },
                singleLine = true,
                textStyle = TextStyle(color = cs.onSurface, fontSize = 16.sp),
                cursorBrush = SolidColor(cs.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 46.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .background(cs.surfaceContainerHigh)
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (input.isEmpty()) {
                            Text(
                                "Tulis pesan atau /perintah",
                                color = cs.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 16.sp
                            )
                        }
                        inner()
                    }
                }
            )
            val ready = input.isNotBlank()
            Box(
                Modifier.size(46.dp).clip(CircleShape)
                    .background(if (ready) cs.primary else cs.surfaceContainerHigh)
                    .clickable(enabled = ready, onClick = { submit() })
                    .semantics { contentDescription = "Kirim" },
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(Glyph.Send, Modifier.size(22.dp), tint = if (ready) cs.onPrimary else cs.onSurfaceVariant)
            }
        }
    }
}

private fun suggestionsFor(input: String, cmds: List<CommandInfo>): List<CommandInfo> {
    if (!input.startsWith("/") || cmds.isEmpty()) return emptyList()
    val word = input.substring(1)
    if (word.contains(' ')) return emptyList()
    return cmds.filter { it.name.startsWith(word, ignoreCase = true) }.take(15)
}

private fun usageHint(input: String, cmds: List<CommandInfo>): String? {
    if (!input.startsWith("/") || !input.contains(' ')) return null
    val name = input.substring(1).substringBefore(' ')
    val c = cmds.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return null
    return if (c.usage.isBlank()) "/" + c.name else "/" + c.name + "   " + c.usage
}

// ======================================================================= Tab Kontrol

@Composable
fun ControlTab(vm: GameSession) {
    val hud = vm.hud
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        // Ukuran tombol arah mengikuti lebar layar supaya muat di HP kecil maupun besar.
        val btn = if (landscape) 58.dp else (((maxWidth.value - 56f) / 6.3f).coerceIn(50f, 68f)).dp
        if (landscape) {
            Row(
                Modifier.fillMaxSize().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                DPad(vm, btn)
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LookPad(vm, hud, Modifier.fillMaxWidth().weight(1f))
                    Hotbar(vm, hud)
                }
                ActionPad(vm, hud, btn, Modifier.width(170.dp))
            }
        } else {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LookPad(vm, hud, Modifier.fillMaxWidth().weight(1f))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    DPad(vm, btn)
                    ActionPad(vm, hud, btn, Modifier.weight(1f))
                }
                Hotbar(vm, hud)
            }
        }
    }
}

@Composable
fun LookPad(vm: GameSession, hud: Hud?, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    val here = if (hud != null) compass(hud.yaw) else ""
    Box(
        modifier
            .clip(shape)
            .background(cs.surfaceContainerLow)
            .border(1.dp, cs.outlineVariant, shape)
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    vm.look(drag.x * 0.3f, drag.y * 0.3f)
                }
            }
    ) {
        Column(
            Modifier.align(Alignment.TopStart).fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Menghadap", fontSize = 12.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(here.ifBlank { "-" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DirPill("Utara", here == "Utara", Modifier.weight(1f)) { vm.faceYaw(180f) }
                DirPill("Timur", here == "Timur", Modifier.weight(1f)) { vm.faceYaw(-90f) }
                DirPill("Selatan", here == "Selatan", Modifier.weight(1f)) { vm.faceYaw(0f) }
                DirPill("Barat", here == "Barat", Modifier.weight(1f)) { vm.faceYaw(90f) }
            }
        }
        Text(
            "Geser jari di area ini\nuntuk menoleh",
            Modifier.align(Alignment.Center),
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
            color = cs.onSurfaceVariant.copy(alpha = 0.8f)
        )
        Row(Modifier.align(Alignment.BottomEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("Ayun tangan") { vm.swing() }
            SmallButton("Pakai item", strong = true) { vm.useItem() }
        }
    }
}

@Composable
fun DirPill(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.height(32.dp).clip(shape)
            .background(if (active) cs.primaryContainer else cs.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) cs.onPrimaryContainer else cs.onSurface,
            maxLines = 1
        )
    }
}

@Composable
fun DPad(vm: GameSession, btn: Dp) {
    Column(
        Modifier.width(btn * 3 + 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HoldButton("Maju", Modifier.size(btn), onHold = { vm.press(Key.FORWARD, it) }) { GlyphIcon(Glyph.Up, Modifier.size(28.dp)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HoldButton("Kiri", Modifier.size(btn), onHold = { vm.press(Key.LEFT, it) }) { GlyphIcon(Glyph.Left, Modifier.size(28.dp)) }
            HoldButton("Mundur", Modifier.size(btn), onHold = { vm.press(Key.BACK, it) }) { GlyphIcon(Glyph.Down, Modifier.size(28.dp)) }
            HoldButton("Kanan", Modifier.size(btn), onHold = { vm.press(Key.RIGHT, it) }) { GlyphIcon(Glyph.Right, Modifier.size(28.dp)) }
        }
    }
}

@Composable
fun ActionPad(vm: GameSession, hud: Hud?, btn: Dp, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hud?.canFly == true) {
            ToggleChip("Terbang", hud.flying, Modifier.fillMaxWidth()) { vm.toggleFly() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleChip("Jongkok", vm.sneakOn, Modifier.weight(1f)) { vm.toggleSneak() }
            ToggleChip("Lari", vm.sprintOn, Modifier.weight(1f)) { vm.toggleSprint() }
        }
        HoldButton(
            "Lompat", Modifier.fillMaxWidth().height(btn), accent = true,
            onHold = { vm.press(Key.JUMP, it) }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlyphIcon(Glyph.Jump, Modifier.size(22.dp))
                Text("Lompat", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun Hotbar(vm: GameSession, hud: Hud?) {
    val cs = MaterialTheme.colorScheme
    val sel = hud?.selectedSlot ?: 0
    val held = hud?.inventory?.getOrNull(36 + sel)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Di tangan: " + (held?.let { it.name + if (it.count > 1) "  \u00D7${it.count}" else "" } ?: "kosong"),
            fontSize = 12.sp,
            color = cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(Modifier.fillMaxWidth()) {
            for (i in 0..8) {
                SlotCell(
                    item = hud?.inventory?.getOrNull(36 + i),
                    selected = sel == i,
                    modifier = Modifier.weight(1f)
                ) { vm.selectSlot(i) }
            }
        }
    }
}

// ======================================================================= Tab Tas

@Composable
fun BagTab(vm: GameSession) {
    val cs = MaterialTheme.colorScheme
    val hud = vm.hud
    val inv = hud?.inventory ?: emptyList()
    val sel = hud?.selectedSlot ?: 0
    val held = inv.getOrNull(36 + sel)
    var picked by remember { mutableStateOf<ItemView?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Panel {
            SectionTitle("Di tangan")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SlotCell(held, true, Modifier.width(56.dp)) { }
                Column(Modifier.weight(1f)) {
                    Text(
                        held?.name ?: "Tangan kosong",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (held != null && held.count > 1) {
                        Text("Jumlah ${held.count}", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                }
                SmallButton("Pakai item", strong = true) { vm.useItem() }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Hotbar  \u2022  ketuk untuk memilih")
            Row(Modifier.fillMaxWidth()) {
                for (i in 0..8) {
                    SlotCell(inv.getOrNull(36 + i), sel == i, Modifier.weight(1f)) { vm.selectSlot(i) }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Tas")
            for (r in 0..2) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0..8) {
                        val item = inv.getOrNull(9 + r * 9 + c)
                        SlotCell(item, false, Modifier.weight(1f)) { picked = item }
                    }
                }
            }
            Text(
                picked?.let { it.name + if (it.count > 1) "  \u00D7${it.count}" else "" } ?: "Ketuk sebuah kotak untuk melihat nama itemnya.",
                fontSize = 13.sp,
                color = cs.onSurfaceVariant
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Perlengkapan")
            Row(Modifier.fillMaxWidth()) {
                val parts = listOf("Kepala" to 5, "Dada" to 6, "Kaki" to 7, "Sepatu" to 8, "Tangan kiri" to 45)
                for ((label, idx) in parts) {
                    val item = inv.getOrNull(idx)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        SlotCell(item, false, Modifier.fillMaxWidth()) { picked = item }
                        Text(label, fontSize = 10.sp, color = cs.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun SlotCell(item: ItemView?, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val dark = isDark()
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(shape)
            .background(if (selected) cs.primaryContainer else cs.surfaceContainerHigh)
            .border(if (selected) 2.dp else 1.dp, if (selected) cs.primary else cs.outlineVariant, shape)
            .clickable(onClick = onClick)
    ) {
        if (item != null) {
            Box(
                Modifier.align(Alignment.Center).fillMaxSize(0.6f)
                    .clip(RoundedCornerShape(6.dp)).background(badgeColor(item.name, dark)),
                contentAlignment = Alignment.Center
            ) {
                Text(initialOf(item.name), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            if (item.count > 1) {
                Text(
                    "${item.count}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp)
                        .clip(RoundedCornerShape(4.dp)).background(Color(0xCC000000))
                        .padding(horizontal = 3.dp)
                )
            }
        }
    }
}

// ======================================================================= Menu dari server

@Composable
fun MenuDialog(vm: GameSession, menu: MenuView) {
    val cs = MaterialTheme.colorScheme
    var showBag by remember { mutableStateOf(false) }
    val own = (menu.slots.size - 36).coerceAtLeast(0)
    val entries = (0 until own).filter { menu.slots[it] != null }

    Dialog(
        onDismissRequest = { vm.closeMenu() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 600.dp)
                .clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainer).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    menu.title.ifBlank { "Menu server" },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { vm.closeMenu() }
                        .semantics { contentDescription = "Tutup" },
                    contentAlignment = Alignment.Center
                ) { GlyphIcon(Glyph.Close, Modifier.size(20.dp)) }
            }
            if (menu.slots.isEmpty()) {
                Text("Memuat...", color = cs.onSurfaceVariant)
            } else {
                Text("Ketuk pilihan untuk memilihnya.", fontSize = 12.sp, color = cs.onSurfaceVariant)
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (entries.isEmpty()) {
                        item { Text("Menu ini kosong.", color = cs.onSurfaceVariant) }
                    }
                    items(entries) { idx ->
                        val slot = menu.slots[idx]
                        if (slot != null) MenuRow(slot) { vm.clickMenuSlot(idx) }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Spacer(Modifier.height(4.dp))
                            SmallButton(if (showBag) "Sembunyikan tas kamu" else "Tampilkan tas kamu") {
                                showBag = !showBag
                            }
                            if (showBag) {
                                for (r in 0..3) {
                                    Row(Modifier.fillMaxWidth()) {
                                        for (c in 0..8) {
                                            val i = own + r * 9 + c
                                            if (i < menu.slots.size) {
                                                SlotCell(menu.slots[i], false, Modifier.weight(1f)) { vm.clickMenuSlot(i) }
                                            } else {
                                                Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MenuRow(item: ItemView, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val dark = isDark()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHigh)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(badgeColor(item.name, dark)),
            contentAlignment = Alignment.Center
        ) { Text(initialOf(item.name), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        Text(item.name, Modifier.weight(1f), fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (item.count > 1) Text("\u00D7${item.count}", fontSize = 13.sp, color = cs.onSurfaceVariant)
    }
}

// ======================================================================= Tab Info

@Composable
fun InfoTab(vm: GameSession, onUseCommand: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val dark = isDark()
    val hud = vm.hud
    var query by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (hud == null) {
            item { Text(vm.status, color = cs.onSurfaceVariant) }
            return@LazyColumn
        }
        item {
            Panel {
                SectionTitle("Dunia")
                InfoRow("Dunia", hud.dimension.substringAfter(':').ifBlank { "-" })
                InfoRow("Mode", modeLabel(hud.gameMode))
                InfoRow("Waktu game", clock(hud.dayTime))
                InfoRow("Posisi", "%.1f, %.1f, %.1f".format(hud.x, hud.y, hud.z))
                InfoRow("Menghadap", compass(hud.yaw))
                InfoRow("Keadaan", if (hud.flying) "Terbang" else if (hud.onGround) "Di tanah" else "Di udara")
                InfoRow("Area dimuat", "${hud.chunkCount} chunk")
            }
        }
        if (hud.header != null || hud.footer != null) {
            item {
                Panel {
                    hud.header?.let { Text(toAnnotated(it, dark), fontSize = 14.sp) }
                    hud.footer?.let { Text(toAnnotated(it, dark), fontSize = 14.sp) }
                }
            }
        }
        item { SectionTitle("Pemain online (${hud.players.size})", Modifier.padding(top = 6.dp)) }
        if (hud.players.isEmpty()) {
            item { Text("Belum ada data pemain.", fontSize = 13.sp, color = cs.onSurfaceVariant) }
        }
        items(hud.players) { p ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainer)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(p.name, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val dot = when {
                    p.ping < 120 -> cs.primary
                    p.ping < 300 -> cs.tertiary
                    else -> cs.error
                }
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(8.dp))
                Text("${p.ping} ms", fontSize = 12.sp, color = cs.onSurfaceVariant, fontFamily = FontFamily.Monospace)
            }
        }
        item { SectionTitle("Perintah server (${hud.commands.size})", Modifier.padding(top = 6.dp)) }
        item {
            AppField(
                label = "Cari perintah",
                value = query,
                onChange = { query = it },
                placeholder = "contoh: tp, gamemode, home",
                helper = "Ketuk perintah untuk menuliskannya di kolom pesan."
            )
        }
        val shown = hud.commands.filter { query.isBlank() || it.name.contains(query.trim().removePrefix("/"), ignoreCase = true) }
        if (shown.isEmpty()) {
            item { Text("Tidak ada perintah yang cocok.", fontSize = 13.sp, color = cs.onSurfaceVariant) }
        }
        items(shown.take(120)) { c ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainer)
                    .clickable { onUseCommand(c.name) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("/" + c.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (c.usage.isNotBlank()) {
                    Text(c.usage, fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.weight(0.4f), fontSize = 14.sp, color = cs.onSurfaceVariant)
        Text(value, Modifier.weight(0.6f), fontSize = 14.sp, textAlign = TextAlign.End)
    }
}

// ======================================================================= Fungsi bantu tampilan

private fun modeLabel(m: String): String = when (m) {
    "SURVIVAL" -> "Survival"
    "CREATIVE" -> "Creative"
    "ADVENTURE" -> "Adventure"
    "SPECTATOR" -> "Spectator"
    else -> m
}

/** yaw 0 = menghadap selatan, 90 = barat, 180 = utara, -90 = timur. */
private fun compass(yaw: Float): String {
    val y = ((yaw % 360f) + 360f) % 360f
    return when {
        y >= 315f || y < 45f -> "Selatan"
        y < 135f -> "Barat"
        y < 225f -> "Utara"
        else -> "Timur"
    }
}

/** Tick 0 = pukul 06.00. */
private fun clock(dayTime: Long): String {
    val t = ((dayTime % 24000L) + 24000L + 6000L) % 24000L
    val h = (t / 1000L).toInt()
    val m = ((t % 1000L) * 60L / 1000L).toInt()
    return "%02d.%02d".format(h, m)
}

private fun initialOf(name: String): String =
    name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"

/** Warna lencana item: stabil per nama, kalem, dan kontras dengan teks putih. */
private fun badgeColor(name: String, dark: Boolean): Color {
    val hue = (abs(name.hashCode()) % 360).toFloat()
    return hsl(hue, 0.42f, if (dark) 0.36f else 0.40f)
}

private fun hsl(h: Float, s: Float, l: Float): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val hp = h / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color(
        ((r1 + m) * 255f).toInt().coerceIn(0, 255),
        ((g1 + m) * 255f).toInt().coerceIn(0, 255),
        ((b1 + m) * 255f).toInt().coerceIn(0, 255)
    )
}

private fun toAnnotated(line: ChatLine, dark: Boolean): AnnotatedString = buildAnnotatedString {
    for (span in line.spans) {
        withStyle(SpanStyle(color = colorOf(span.rgb, dark))) { append(span.text) }
    }
}

/** Warna chat Minecraft disesuaikan: dicerahkan di tema gelap, digelapkan di tema terang. */
private fun colorOf(rgb: Int?, dark: Boolean): Color {
    if (rgb == null) return Color.Unspecified
    var r = (rgb shr 16) and 0xFF
    var g = (rgb shr 8) and 0xFF
    var b = rgb and 0xFF
    val luma = 0.299 * r + 0.587 * g + 0.114 * b
    if (dark && luma < 90) {
        r += ((255 - r) * 0.45).toInt()
        g += ((255 - g) * 0.45).toInt()
        b += ((255 - b) * 0.45).toInt()
    } else if (!dark && luma > 150) {
        r = (r * 0.55).toInt()
        g = (g * 0.55).toInt()
        b = (b * 0.55).toInt()
    }
    return Color(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
}

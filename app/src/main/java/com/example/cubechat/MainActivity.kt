package com.example.cubechat

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cubechat.net.ChatLine
import com.example.cubechat.net.CommandInfo
import com.example.cubechat.net.Hud
import com.example.cubechat.net.ItemView
import com.example.cubechat.net.Key
import com.example.cubechat.net.McClient
import com.example.cubechat.net.MenuView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ======================================================================= ViewModel

class ChatViewModel(app: Application) : AndroidViewModel(app), McClient.Listener {
    private val main = Handler(Looper.getMainLooper())
    private val prefs = app.getSharedPreferences("cubechat", Context.MODE_PRIVATE)
    private val client = McClient(app, this)
    private var pollJob: Job? = null

    val lines = mutableStateListOf<ChatLine>()
    var status by mutableStateOf("Belum terhubung")
    var connected by mutableStateOf(false)
    var hud by mutableStateOf<Hud?>(null)
    var sneakOn by mutableStateOf(false)
    var sprintOn by mutableStateOf(false)

    var lastAddress by mutableStateOf(prefs.getString("address", "") ?: "")
    var lastUser by mutableStateOf(prefs.getString("user", "") ?: "")
    val recent = mutableStateListOf<String>().apply {
        addAll((prefs.getString("recent", "") ?: "").split('\n').filter { it.isNotBlank() })
    }

    private val sent = ArrayList<String>()
    private var historyPos = 0

    fun connect(address: String, username: String) {
        val a = address.trim()
        val u = username.trim()
        lines.clear()
        hud = null
        connected = true
        status = "Menghubungkan..."
        lastAddress = a
        lastUser = u
        val list = (listOf(a) + recent.filter { it != a }).take(5)
        recent.clear()
        recent.addAll(list)
        prefs.edit()
            .putString("address", a)
            .putString("user", u)
            .putString("recent", list.joinToString("\n"))
            .apply()
        client.connect(a, u)
        client.setSneak(sneakOn)
        client.setSprint(sprintOn)
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                hud = withContext(Dispatchers.Default) { client.hud() }
                delay(200)
            }
        }
    }

    fun disconnect() {
        client.disconnect()
        pollJob?.cancel()
        connected = false
        hud = null
        status = "Terputus"
    }

    fun send(text: String) {
        sent.add(text)
        if (sent.size > 50) sent.removeAt(0)
        historyPos = sent.size
        client.sendChat(text)
    }

    /** Ambil pesan lama (delta -1 = lebih lama). Mengembalikan null kalau tidak ada. */
    fun recall(delta: Int): String? {
        if (sent.isEmpty()) return null
        historyPos = (historyPos + delta).coerceIn(0, sent.size - 1)
        return sent[historyPos]
    }

    fun press(k: Key, down: Boolean) = client.press(k, down)
    fun look(dYaw: Float, dPitch: Float) = client.look(dYaw, dPitch)
    fun faceYaw(yaw: Float) = client.faceYaw(yaw)
    fun toggleFly() = client.toggleFly()
    fun selectSlot(i: Int) = client.selectSlot(i)
    fun useItem() = client.useItem()
    fun swing() = client.swing()
    fun clickMenuSlot(i: Int) = client.clickMenuSlot(i)
    fun closeMenu() = client.closeMenu()
    fun respawn() = client.respawn()

    fun toggleSneak() {
        sneakOn = !sneakOn
        client.setSneak(sneakOn)
    }

    fun toggleSprint() {
        sprintOn = !sprintOn
        client.setSprint(sprintOn)
    }

    override fun onChat(line: ChatLine) {
        main.post {
            lines.add(line)
            if (lines.size > 500) lines.removeAt(0)
        }
    }

    override fun onStatus(text: String) {
        main.post { status = text }
    }

    override fun onDisconnected(reason: String) {
        main.post {
            connected = false
            status = reason
            hud = null
            pollJob?.cancel()
        }
    }

    override fun onCleared() {
        client.disconnect()
    }
}

// ======================================================================= Activity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

@Composable
fun App(vm: ChatViewModel = viewModel()) {
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        if (vm.connected) MainScreen(vm) else ConnectScreen(vm)
    }
}

// ======================================================================= Layar sambung

@Composable
fun ConnectScreen(vm: ChatViewModel) {
    var address by rememberSaveable { mutableStateOf(vm.lastAddress) }
    var user by rememberSaveable {
        mutableStateOf(vm.lastUser.ifBlank { "Cube" + (1000..9999).random() })
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("CubeChat", style = MaterialTheme.typography.headlineMedium)
        Text("Klien Minecraft Java 1.21.11 untuk HP", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Server (host atau host:port)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (vm.recent.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(vm.recent.toList()) { r ->
                    AssistChip(onClick = { address = r }, label = { Text(r) })
                }
            }
        }
        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("Username (mode offline)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { vm.connect(address, user) },
            enabled = address.isNotBlank() && user.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Connect") }
        Text(vm.status, style = MaterialTheme.typography.bodyMedium)
    }
}

// ======================================================================= Layar utama

private val TABS = listOf("💬" to "Chat", "🎮" to "Kontrol", "🎒" to "Inventori", "ℹ" to "Info")

@Composable
fun MainScreen(vm: ChatViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val hud = vm.hud

    Scaffold(
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(t.first) },
                        label = { Text(t.second) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                StatusBar(vm, hud)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        0 -> ChatTab(vm)
                        1 -> ControlTab(vm)
                        2 -> InventoryTab(vm)
                        else -> InfoTab(vm)
                    }
                }
            }
            Overlay(vm, hud)
        }
    }

    val menu = hud?.menu
    if (menu != null) MenuDialog(vm, menu)
}

@Composable
fun StatusBar(vm: ChatViewModel, hud: Hud?) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        if (hud == null) {
            Text(vm.status, fontSize = 12.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("❤ ${hud.health.toInt()}   🍖 ${hud.food}   ${modeLabel(hud.gameMode)}", fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "Putus",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { vm.disconnect() }.padding(4.dp)
                )
            }
            LinearProgressIndicator(
                progress = { (hud.health / 20f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp)
            )
            Text(
                "x %.1f  y %.1f  z %.1f  %s".format(hud.x, hud.y, hud.z, if (hud.spawned) facing(hud.yaw) else vm.status),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun Overlay(vm: ChatViewModel, hud: Hud?) {
    if (hud == null) return
    Box(Modifier.fillMaxSize()) {
        if (hud.title != null || hud.subtitle != null) {
            Column(
                Modifier.align(Alignment.Center)
                    .background(Color(0x99000000), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                hud.title?.let {
                    Text(toAnnotated(it), fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
                hud.subtitle?.let { Text(toAnnotated(it), fontSize = 16.sp, textAlign = TextAlign.Center) }
            }
        }
        hud.actionBar?.let {
            Text(
                toAnnotated(it),
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                    .background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(8.dp)
            )
        }
        if (hud.dead) {
            Column(
                Modifier.align(Alignment.Center)
                    .background(Color(0xCC000000), RoundedCornerShape(12.dp)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("☠ Kamu mati", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Button(onClick = { vm.respawn() }) { Text("Respawn") }
            }
        }
    }
}

// ======================================================================= Tab Chat

@Composable
fun ChatTab(vm: ChatViewModel) {
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    val cmds = vm.hud?.commands ?: emptyList()

    LaunchedEffect(vm.lines.size) {
        if (vm.lines.isNotEmpty()) listState.animateScrollToItem(vm.lines.size - 1)
    }

    fun submit() {
        if (input.isNotBlank()) {
            vm.send(input)
            input = ""
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
            items(vm.lines) { line -> Text(toAnnotated(line), fontSize = 14.sp) }
        }

        val suggestions = suggestionsFor(input, cmds)
        if (suggestions.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(suggestions) { c ->
                    AssistChip(onClick = { input = "/" + c.name + " " }, label = { Text("/" + c.name) })
                }
            }
        }
        val hint = usageHint(input, cmds)
        if (hint != null) Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.recall(-1)?.let { input = it } }) { Text("↑") }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Pesan atau /perintah") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { submit() }) { Text("Kirim") }
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
    return if (c.usage.isBlank()) "/" + c.name else "/" + c.name + "  " + c.usage
}

// ======================================================================= Tab Kontrol

@Composable
fun ControlTab(vm: ChatViewModel) {
    val hud = vm.hud
    Column(
        Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LookPad(vm, Modifier.fillMaxWidth().weight(1f))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            DPad(vm)
            ActionPad(vm, hud)
        }
        HotbarRow(vm, hud)
    }
}

@Composable
fun LookPad(vm: ChatViewModel, modifier: Modifier) {
    val hud = vm.hud
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    vm.look(drag.x * 0.3f, drag.y * 0.3f)
                }
            }
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Geser di sini untuk menoleh", fontSize = 13.sp)
            if (hud != null) {
                Text(
                    "${facing(hud.yaw)}   yaw %.0f°  pitch %.0f°".format(hud.yaw, hud.pitch),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { vm.faceYaw(180f) }) { Text("U") }
                OutlinedButton(onClick = { vm.faceYaw(-90f) }) { Text("T") }
                OutlinedButton(onClick = { vm.faceYaw(0f) }) { Text("S") }
                OutlinedButton(onClick = { vm.faceYaw(90f) }) { Text("B") }
            }
        }
    }
}

@Composable
fun DPad(vm: ChatViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HoldButton("▲", Modifier.size(64.dp)) { vm.press(Key.FORWARD, it) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HoldButton("◀", Modifier.size(64.dp)) { vm.press(Key.LEFT, it) }
            HoldButton("▼", Modifier.size(64.dp)) { vm.press(Key.BACK, it) }
            HoldButton("▶", Modifier.size(64.dp)) { vm.press(Key.RIGHT, it) }
        }
    }
}

@Composable
fun ActionPad(vm: ChatViewModel, hud: Hud?) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = vm.sneakOn, onClick = { vm.toggleSneak() }, label = { Text("Jongkok") })
            FilterChip(selected = vm.sprintOn, onClick = { vm.toggleSprint() }, label = { Text("Lari") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = hud?.flying == true,
                onClick = { vm.toggleFly() },
                enabled = hud?.canFly == true,
                label = { Text("Terbang") }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalButton(onClick = { vm.useItem() }) { Text("Klik kanan") }
            OutlinedButton(onClick = { vm.swing() }) { Text("Ayun") }
        }
        HoldButton("LOMPAT", Modifier.width(150.dp).height(64.dp)) { vm.press(Key.JUMP, it) }
    }
}

/** Tombol yang aktif selama ditahan (untuk gerak). */
@Composable
fun HoldButton(label: String, modifier: Modifier = Modifier, onHold: (Boolean) -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val bg = if (pressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
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
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun HotbarRow(vm: ChatViewModel, hud: Hud?) {
    Column {
        val sel = hud?.selectedSlot ?: 0
        val held = hud?.inventory?.getOrNull(36 + sel)
        Text(
            "Di tangan: " + (held?.let { it.name + if (it.count > 1) " x${it.count}" else "" } ?: "(kosong)"),
            fontSize = 12.sp
        )
        Row(Modifier.fillMaxWidth()) {
            for (i in 0..8) {
                SlotCell(
                    item = hud?.inventory?.getOrNull(36 + i),
                    selected = hud?.selectedSlot == i,
                    modifier = Modifier.weight(1f)
                ) { vm.selectSlot(i) }
            }
        }
    }
}

// ======================================================================= Tab Inventori

@Composable
fun InventoryTab(vm: ChatViewModel) {
    val hud = vm.hud
    val inv = hud?.inventory ?: emptyList()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Hotbar (ketuk untuk memilih)", style = MaterialTheme.typography.titleSmall)
        HotbarRow(vm, hud)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { vm.useItem() }) { Text("Klik kanan (pakai item)") }
            OutlinedButton(onClick = { vm.swing() }) { Text("Ayun") }
        }
        HorizontalDivider()
        Text("Inventori", style = MaterialTheme.typography.titleSmall)
        for (r in 0..2) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0..8) {
                    SlotCell(inv.getOrNull(9 + r * 9 + c), false, Modifier.weight(1f)) { }
                }
            }
        }
        HorizontalDivider()
        Text("Armor (kepala, dada, kaki, sepatu) dan tangan kiri", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth()) {
            for (i in listOf(5, 6, 7, 8, 45)) {
                SlotCell(inv.getOrNull(i), false, Modifier.weight(1f)) { }
            }
            Spacer(Modifier.weight(4f))
        }
    }
}

@Composable
fun SlotCell(item: ItemView?, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .clickable(onClick = onClick)
    ) {
        if (item != null) {
            Text(
                item.name,
                fontSize = 8.sp,
                lineHeight = 9.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopStart).padding(2.dp)
            )
            if (item.count > 1) {
                Text(
                    "${item.count}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp)
                )
            }
        }
    }
}

// ======================================================================= Menu server (kotak inventori dari server)

@Composable
fun MenuDialog(vm: ChatViewModel, menu: MenuView) {
    Dialog(
        onDismissRequest = { vm.closeMenu() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxWidth().padding(8.dp), shape = RoundedCornerShape(16.dp)) {
            Column(
                Modifier.padding(10.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(menu.title.ifBlank { "Menu" }, style = MaterialTheme.typography.titleMedium)
                if (menu.slots.isEmpty()) {
                    Text("Memuat...", fontSize = 12.sp)
                } else {
                    val own = (menu.slots.size - 36).coerceAtLeast(0)
                    SlotGrid(menu.slots.subList(0, own), 0) { vm.clickMenuSlot(it) }
                    if (own > 0) HorizontalDivider()
                    Text("Inventori kamu", fontSize = 11.sp)
                    SlotGrid(menu.slots.subList(own, menu.slots.size), own) { vm.clickMenuSlot(it) }
                }
                TextButton(onClick = { vm.closeMenu() }) { Text("Tutup") }
            }
        }
    }
}

@Composable
fun SlotGrid(slots: List<ItemView?>, offset: Int, onClick: (Int) -> Unit) {
    val rows = (slots.size + 8) / 9
    for (r in 0 until rows) {
        Row(Modifier.fillMaxWidth()) {
            for (c in 0..8) {
                val i = r * 9 + c
                if (i < slots.size) {
                    SlotCell(slots[i], false, Modifier.weight(1f)) { onClick(offset + i) }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ======================================================================= Tab Info

@Composable
fun InfoTab(vm: ChatViewModel) {
    val hud = vm.hud
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (hud == null) {
            item { Text(vm.status) }
            return@LazyColumn
        }
        item { Text("Dunia", style = MaterialTheme.typography.titleSmall) }
        item {
            Text(
                "${hud.dimension.ifBlank { "?" }}\nMode: ${modeLabel(hud.gameMode)}   Waktu: ${clock(hud.dayTime)}\n" +
                    "Chunk dimuat: ${hud.chunkCount}   ${if (hud.onGround) "di tanah" else "di udara"}" +
                    if (hud.flying) "   (terbang)" else "",
                fontSize = 13.sp
            )
        }
        if (hud.header != null || hud.footer != null) {
            item { HorizontalDivider() }
            hud.header?.let { h -> item { Text(toAnnotated(h), fontSize = 13.sp) } }
            hud.footer?.let { f -> item { Text(toAnnotated(f), fontSize = 13.sp) } }
        }
        item { HorizontalDivider() }
        item { Text("Pemain online (${hud.players.size})", style = MaterialTheme.typography.titleSmall) }
        items(hud.players) { p ->
            Row(Modifier.fillMaxWidth()) {
                Text(p.name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("${p.ping} ms", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
        item { HorizontalDivider() }
        item { Text("Perintah server (${hud.commands.size})", style = MaterialTheme.typography.titleSmall) }
        items(hud.commands) { c ->
            Text(
                "/" + c.name + if (c.usage.isBlank()) "" else "  " + c.usage,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ======================================================================= util tampilan

private fun modeLabel(m: String): String = when (m) {
    "SURVIVAL" -> "Survival"
    "CREATIVE" -> "Creative"
    "ADVENTURE" -> "Adventure"
    "SPECTATOR" -> "Spectator"
    else -> m
}

/** yaw 0 = menghadap selatan (+Z), 90 = barat (-X), 180 = utara (-Z), -90 = timur (+X). */
private fun facing(yaw: Float): String {
    val y = ((yaw % 360f) + 360f) % 360f
    return when {
        y >= 315f || y < 45f -> "Selatan (+Z)"
        y < 135f -> "Barat (-X)"
        y < 225f -> "Utara (-Z)"
        else -> "Timur (+X)"
    }
}

/** Tick 0 = pukul 06:00. */
private fun clock(dayTime: Long): String {
    val t = ((dayTime % 24000L) + 24000L + 6000L) % 24000L
    val h = (t / 1000L).toInt()
    val m = ((t % 1000L) * 60L / 1000L).toInt()
    return "%02d:%02d".format(h, m)
}

private fun toAnnotated(line: ChatLine): AnnotatedString = buildAnnotatedString {
    for (span in line.spans) {
        withStyle(SpanStyle(color = colorOf(span.rgb))) { append(span.text) }
    }
}

/** Warna gelap (mis. biru tua) dicerahkan sedikit supaya terbaca di latar gelap. */
private fun colorOf(rgb: Int?): Color {
    if (rgb == null) return Color.Unspecified
    var r = (rgb shr 16) and 0xFF
    var g = (rgb shr 8) and 0xFF
    var b = rgb and 0xFF
    val luma = 0.299 * r + 0.587 * g + 0.114 * b
    if (luma < 90) {
        r += ((255 - r) * 0.45).toInt()
        g += ((255 - g) * 0.45).toInt()
        b += ((255 - b) * 0.45).toInt()
    }
    return Color(0xFF shl 24 or (r shl 16) or (g shl 8) or b)
}

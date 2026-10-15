package com.example.cubechat

import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cubechat.net.ChatLine
import com.example.cubechat.net.McClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChatViewModel(app: Application) : AndroidViewModel(app), McClient.Listener {
    private val main = Handler(Looper.getMainLooper())
    private val client = McClient(app, this)
    private var hudJob: Job? = null

    val lines = mutableStateListOf<ChatLine>()
    var status by mutableStateOf("Belum terhubung")
    var connected by mutableStateOf(false)
    var hud by mutableStateOf("")

    fun connect(address: String, username: String) {
        lines.clear()
        connected = true
        status = "Menghubungkan..."
        client.connect(address.trim(), username.trim())
        hudJob?.cancel()
        hudJob = viewModelScope.launch {
            while (true) {
                hud = client.hudText()
                delay(500)
            }
        }
    }

    fun send(text: String) = client.sendChat(text)

    fun disconnect() {
        client.disconnect()
        hudJob?.cancel()
        connected = false
        status = "Terputus"
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
            hudJob?.cancel()
        }
    }

    override fun onCleared() {
        client.disconnect()
    }
}

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
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
        if (vm.connected) ChatScreen(vm) else ConnectForm(vm)
    }
}

@Composable
fun ConnectForm(vm: ChatViewModel) {
    var address by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("Cube" + (1000..9999).random()) }

    Text("CubeChat", style = MaterialTheme.typography.headlineMedium)
    Text("Klien chat Minecraft 1.21.11", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = address,
        onValueChange = { address = it },
        label = { Text("Server (host atau host:port)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = user,
        onValueChange = { user = it },
        label = { Text("Username (mode offline)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = { vm.connect(address, user) },
        enabled = address.isNotBlank() && user.isNotBlank()
    ) { Text("Connect") }
    Spacer(Modifier.height(8.dp))
    Text(vm.status, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun ColumnScope.ChatScreen(vm: ChatViewModel) {
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(vm.lines.size) {
        if (vm.lines.isNotEmpty()) listState.animateScrollToItem(vm.lines.size - 1)
    }

    Text(
        vm.status + "\n" + vm.hud,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp
    )
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
        items(vm.lines) { line -> Text(toAnnotated(line), fontSize = 14.sp) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text("Pesan atau /perintah") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = {
            if (input.isNotBlank()) {
                vm.send(input)
                input = ""
            }
        }) { Text("Kirim") }
    }
    TextButton(onClick = { vm.disconnect() }) { Text("Putus") }
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

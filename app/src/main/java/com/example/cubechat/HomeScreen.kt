package com.example.cubechat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.cubechat.data.ServerEntry

// ======================================================================= Beranda

@Composable
fun HomeScreen(app: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    var showAdd by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<ServerEntry?>(null) }
    var joinTarget by remember { mutableStateOf<ServerEntry?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("CubeChat", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Pilih server lalu masuk. Kamu bisa aktif di beberapa server sekaligus.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }
        }

        if (app.sessions.isNotEmpty()) {
            item { SectionTitle("Sedang aktif", Modifier.padding(top = 8.dp)) }
            items(app.sessions.toList()) { s -> SessionCard(app, s) }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Server", Modifier.weight(1f))
                SmallButton("Tambah server") { showAdd = true }
            }
        }
        if (app.servers.isEmpty()) {
            item {
                Panel {
                    Text("Belum ada server.", fontWeight = FontWeight.Medium)
                    Text(
                        "Ketuk \"Tambah server\", isi alamat server dan nama pemain, lalu masuk.",
                        fontSize = 13.sp,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }
        items(app.servers.toList()) { sv ->
            ServerCard(app, sv, onOther = { joinTarget = sv }, onEdit = { editTarget = sv })
        }

        item { SectionTitle("Akun", Modifier.padding(top = 8.dp)) }
        item { AccountsPanel(app) }

        item {
            Text(
                "Satu server bisa dipakai maksimal ${AppViewModel.MAX_PER_SERVER} akun sekaligus. " +
                    "Password hanya disimpan kalau kamu setuju, dan disimpan terenkripsi di HP ini.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    if (showAdd) ServerDialog(null, app) { showAdd = false }
    val et = editTarget
    if (et != null) ServerDialog(et, app) { editTarget = null }
    val jt = joinTarget
    if (jt != null) JoinDialog(app, jt) { joinTarget = null }
}

@Composable
fun SessionCard(app: AppViewModel, s: GameSession) {
    val cs = MaterialTheme.colorScheme
    val hud = s.hud
    val ready = hud != null && hud.spawned
    val state = when {
        !s.alive -> "Terputus"
        hud == null -> "Menghubungkan..."
        !hud.spawned -> "Memuat dunia..."
        else -> "Terhubung"
    }
    val dot = when {
        !s.alive -> cs.error
        ready -> cs.primary
        else -> cs.tertiary
    }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            Column(Modifier.weight(1f)) {
                Text(
                    s.server.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    s.username + "  \u2022  " + state,
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("Buka", Modifier.weight(1f), strong = true) { app.open(s) }
            SmallButton(if (s.alive) "Keluar" else "Hapus", Modifier.weight(1f)) { app.closeSession(s) }
        }
    }
}

@Composable
fun ServerCard(app: AppViewModel, sv: ServerEntry, onOther: () -> Unit, onEdit: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var confirmDelete by remember { mutableStateOf(false) }
    val active = app.activeCount(sv)
    val hasAuto = sv.defaultUser.isNotBlank() && app.hasSaved(sv, sv.defaultUser)
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                sv.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                sv.address,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val account = if (sv.defaultUser.isBlank()) "Belum ada akun bawaan" else "Akun: " + sv.defaultUser
            val extra = (if (hasAuto) "  \u2022  login otomatis" else "") +
                (if (active > 0) "  \u2022  $active/${AppViewModel.MAX_PER_SERVER} aktif" else "")
            Text(account + extra, style = MaterialTheme.typography.labelMedium, color = cs.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("Masuk", Modifier.weight(1f), strong = true) {
                if (sv.defaultUser.isBlank()) onOther() else app.join(sv, sv.defaultUser)
            }
            SmallButton("Akun lain", Modifier.weight(1f)) { onOther() }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onEdit) { Text("Ubah") }
            TextButton(onClick = { confirmDelete = true }) { Text("Hapus", color = cs.error) }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus server ini?") },
            text = { Text("${sv.name} dan password login otomatis untuk server ini akan dihapus dari HP.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    app.removeServer(sv)
                }) { Text("Hapus", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } }
        )
    }
}

@Composable
fun AccountsPanel(app: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    var newName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    Panel {
        if (app.accounts.isEmpty()) {
            Text(
                "Belum ada akun. Akun tersimpan otomatis saat kamu masuk ke server, atau tambahkan di bawah.",
                fontSize = 13.sp,
                color = cs.onSurfaceVariant
            )
        }
        for (a in app.accounts.toList()) {
            val n = app.savedCount(a)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (n > 0) "Login otomatis di $n server" else "Belum ada password tersimpan",
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant
                    )
                }
                TextButton(onClick = { deleteTarget = a }) { Text("Hapus", color = cs.error) }
            }
        }
        if (app.accounts.isNotEmpty()) HorizontalDivider(color = cs.outlineVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppField(
                label = "Tambah akun",
                value = newName,
                onChange = { newName = it },
                modifier = Modifier.weight(1f),
                placeholder = "nama pemain"
            )
            SmallButton("Tambah") {
                if (app.addAccount(newName)) newName = ""
            }
        }
    }
    val dt = deleteTarget
    if (dt != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Hapus akun $dt?") },
            text = { Text("Password login otomatis untuk akun ini juga ikut dihapus.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    app.removeAccount(dt)
                }) { Text("Hapus", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Batal") } }
        )
    }
}

// ======================================================================= Dialog

@Composable
fun FormDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 480.dp).fillMaxWidth().heightIn(max = 640.dp)
                .clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainer)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun ServerDialog(initial: ServerEntry?, app: AppViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var user by remember { mutableStateOf(initial?.defaultUser ?: (app.accounts.firstOrNull() ?: "")) }
    FormDialog(if (initial == null) "Tambah server" else "Ubah server", onDismiss) {
        AppField(
            label = "Nama (boleh dikosongkan)",
            value = name,
            onChange = { name = it },
            placeholder = "contoh: Server Survival"
        )
        AppField(
            label = "Alamat server",
            value = address,
            onChange = { address = it },
            placeholder = "contoh: play.namaserver.com",
            helper = "Port boleh ditulis, misalnya play.namaserver.com:25565",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        AppField(
            label = "Akun bawaan",
            value = user,
            onChange = { user = it },
            placeholder = "nama pemain"
        )
        if (app.accounts.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(app.accounts.toList()) { a -> SmallButton(a) { user = a } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("Batal", Modifier.weight(1f)) { onDismiss() }
            SmallButton("Simpan", Modifier.weight(1f), strong = true) {
                if (app.saveServer(initial, name, address, user)) onDismiss()
            }
        }
    }
}

@Composable
fun JoinDialog(app: AppViewModel, server: ServerEntry, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var newUser by remember { mutableStateOf("") }
    FormDialog("Masuk ke " + server.name, onDismiss) {
        if (app.accounts.isNotEmpty()) {
            SectionTitle("Pilih akun")
            for (a in app.accounts.toList()) {
                val saved = app.hasSaved(server, a)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHigh)
                        .clickable { if (app.join(server, a)) onDismiss() }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(a, Modifier.weight(1f), fontSize = 15.sp)
                    if (saved) Text("login otomatis", fontSize = 12.sp, color = cs.primary)
                }
            }
        }
        AppField(
            label = if (app.accounts.isEmpty()) "Nama pemain" else "Atau pakai nama baru",
            value = newUser,
            onChange = { newUser = it },
            placeholder = "nama pemain"
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("Batal", Modifier.weight(1f)) { onDismiss() }
            SmallButton("Masuk", Modifier.weight(1f), strong = true) {
                if (app.join(server, newUser)) onDismiss()
            }
        }
    }
}

/** Pertanyaan simpan password dan pesan singkat, ditampilkan di atas layar apa pun. */
@Composable
fun AppDialogs(app: AppViewModel) {
    val p = app.savePrompt
    if (p != null) {
        AlertDialog(
            onDismissRequest = { app.dismissSave() },
            title = { Text("Simpan untuk login otomatis?") },
            text = {
                Text(
                    buildString {
                        append("Akun ").append(p.session.username).append(" di ").append(p.session.server.name).append(". ")
                        if (p.register) append("Terdeteksi pendaftaran akun baru. ")
                        if (p.update) append("Password lama yang tersimpan akan diganti. ")
                        append("Password disimpan terenkripsi di HP ini dan dikirim otomatis (/login) setiap kamu masuk lagi.")
                    }
                )
            },
            confirmButton = { TextButton(onClick = { app.confirmSave() }) { Text("Simpan") } },
            dismissButton = { TextButton(onClick = { app.dismissSave() }) { Text("Jangan") } }
        )
    }
    val n = app.notice
    if (n != null) {
        AlertDialog(
            onDismissRequest = { app.notice = null },
            text = { Text(n) },
            confirmButton = { TextButton(onClick = { app.notice = null }) { Text("OK") } }
        )
    }
}

// ======================================================================= Pindah sesi

@Composable
fun SessionBar(app: AppViewModel, current: GameSession) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(cs.surfaceContainerLow).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SmallButton("Beranda") { app.goHome() }
        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(app.sessions.toList()) { s -> SessionChip(s, s === current) { app.open(s) } }
        }
    }
}

@Composable
fun SessionChip(s: GameSession, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val hud = s.hud
    val dot = when {
        !s.alive -> cs.error
        hud != null && hud.spawned -> cs.primary
        else -> cs.tertiary
    }
    Row(
        Modifier.heightIn(min = 42.dp).widthIn(max = 200.dp).clip(shape)
            .background(if (selected) cs.primaryContainer else cs.surfaceContainerHigh)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(
            s.server.name + " \u2022 " + s.username,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) cs.onPrimaryContainer else cs.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

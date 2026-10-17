package com.example.cubechat

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cubechat.data.KeystoreVault
import com.example.cubechat.data.PrefsKeyValue
import com.example.cubechat.data.ServerEntry
import com.example.cubechat.data.Store
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Pertanyaan "Simpan untuk login otomatis?" yang sedang menunggu jawaban pemain. */
class SavePrompt(
    val session: GameSession,
    val password: String,
    val register: Boolean,
    val update: Boolean
)

class AppViewModel(private val appContext: Application) : AndroidViewModel(appContext) {
    companion object {
        /** Satu server boleh dipakai paling banyak 2 akun sekaligus. */
        const val MAX_PER_SERVER = 2

        /** Batas pengaman memori HP: jumlah sesi aktif di semua server. */
        const val MAX_SESSIONS = 6
    }

    private val store = Store(
        PrefsKeyValue(appContext.getSharedPreferences("cubechat", Context.MODE_PRIVATE)),
        KeystoreVault()
    )

    val servers = mutableStateListOf<ServerEntry>()
    val accounts = mutableStateListOf<String>()
    val sessions = mutableStateListOf<GameSession>()

    /** Sesi yang sedang dibuka. Null = layar Beranda. */
    var current by mutableStateOf<GameSession?>(null)

    /** Pesan singkat untuk pemain (kesalahan, batas, dll). */
    var notice by mutableStateOf<String?>(null)
    var savePrompt by mutableStateOf<SavePrompt?>(null)

    private var nextId = 1

    init {
        servers.addAll(store.loadServers())
        accounts.addAll(store.loadAccounts())
        startPolling()
    }

    // ------------------------------------------------------------ polling

    private fun startPolling() {
        viewModelScope.launch {
            var tick = 0
            while (isActive) {
                val cur = current
                // Sesi yang sedang dibuka diperbarui cepat, sesi lain pelan-pelan.
                for (s in sessions.toList()) {
                    if (s.alive && (s === cur || tick % 5 == 0)) s.refresh()
                }
                tick++
                delay(200)
            }
        }
    }

    // -------------------------------------------------------------- server

    private fun cleanAddress(a: String) = a.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')

    /** Mengembalikan true kalau tersimpan. */
    fun saveServer(editing: ServerEntry?, name: String, address: String, user: String): Boolean {
        val addr = cleanAddress(address)
        if (addr.isEmpty() || addr.contains(' ')) {
            notice = "Alamat server belum benar. Contoh: play.namaserver.com"
            return false
        }
        val u = user.trim()
        if (u.isNotEmpty()) {
            val bad = nameProblem(u)
            if (bad != null) {
                notice = bad
                return false
            }
        }
        val nm = name.trim().ifEmpty { addr }
        if (editing == null) {
            servers.add(ServerEntry(store.newId(), nm, addr, u))
        } else {
            val i = servers.indexOfFirst { it.id == editing.id }
            if (i >= 0) {
                if (!editing.address.equals(addr, ignoreCase = true)) {
                    if (sessions.any { it.alive && it.server.id == editing.id }) {
                        notice = "Server ini sedang aktif. Keluar dulu sebelum mengubah alamatnya."
                        return false
                    }
                    store.moveServerPasswords(editing.address, addr)
                }
                servers[i] = ServerEntry(editing.id, nm, addr, u)
            }
        }
        if (u.isNotEmpty()) rememberAccount(u)
        store.saveServers(servers.toList())
        return true
    }

    fun removeServer(sv: ServerEntry) {
        if (sessions.any { it.alive && it.server.id == sv.id }) {
            notice = "Server ini sedang aktif. Keluar dulu sebelum menghapusnya."
            return
        }
        sessions.filter { it.server.id == sv.id }.forEach { closeSession(it) }
        servers.removeAll { it.id == sv.id }
        store.deleteForServer(sv.address)
        store.saveServers(servers.toList())
    }

    fun activeCount(sv: ServerEntry): Int = sessions.count { it.alive && it.server.id == sv.id }

    // ---------------------------------------------------------------- akun

    private fun nameProblem(u: String): String? = when {
        u.isEmpty() -> "Nama pemain belum diisi."
        u.length > 16 -> "Nama pemain maksimal 16 karakter."
        u.any { it.isWhitespace() } -> "Nama pemain tidak boleh pakai spasi."
        else -> null
    }

    private fun rememberAccount(u: String) {
        if (accounts.none { it.equals(u, ignoreCase = true) }) {
            accounts.add(u)
            store.saveAccounts(accounts.toList())
        }
    }

    fun addAccount(name: String): Boolean {
        val u = name.trim()
        val bad = nameProblem(u)
        if (bad != null) {
            notice = bad
            return false
        }
        rememberAccount(u)
        return true
    }

    fun removeAccount(name: String) {
        if (sessions.any { it.alive && it.username.equals(name, ignoreCase = true) }) {
            notice = "Akun ini sedang dipakai. Keluar dulu dari server-nya."
            return
        }
        accounts.removeAll { it.equals(name, ignoreCase = true) }
        store.saveAccounts(accounts.toList())
        store.deleteForUser(name)
    }

    fun savedCount(name: String): Int = store.countForUser(name)

    fun hasSaved(sv: ServerEntry, user: String): Boolean = store.hasPassword(sv.address, user)

    // -------------------------------------------------------------- sesi

    private fun joinProblem(sv: ServerEntry, u: String, except: GameSession?): String? {
        nameProblem(u)?.let { return it }
        val others = sessions.filter { it.alive && it !== except }
        val onServer = others.filter { it.server.id == sv.id }
        if (onServer.any { it.username.equals(u, ignoreCase = true) }) {
            return "Akun $u sudah aktif di server ini."
        }
        if (onServer.size >= MAX_PER_SERVER) {
            return "Satu server maksimal dipakai $MAX_PER_SERVER akun sekaligus."
        }
        if (others.size >= MAX_SESSIONS) {
            return "Sesi aktif sudah $MAX_SESSIONS. Keluar dari salah satu dulu supaya HP tidak berat."
        }
        return null
    }

    /** Masuk ke server dengan akun tertentu. Mengembalikan true kalau sesi dibuat. */
    fun join(sv: ServerEntry, username: String): Boolean {
        val u = username.trim()
        val problem = joinProblem(sv, u, null)
        if (problem != null) {
            notice = problem
            return false
        }
        rememberAccount(u)
        val s = GameSession(
            nextId++, sv, u, appContext,
            passwordProvider = { store.password(sv.address, u) },
            onAuthCommand = { sess, pw, reg -> requestSave(sess, pw, reg) }
        )
        sessions.add(s)
        current = s
        s.start()
        return true
    }

    fun reconnect(s: GameSession) {
        val problem = joinProblem(s.server, s.username, s)
        if (problem != null) {
            notice = problem
            return
        }
        s.reconnect()
    }

    fun open(s: GameSession) {
        current = s
    }

    fun goHome() {
        current = null
    }

    fun closeSession(s: GameSession) {
        s.disconnect()
        sessions.remove(s)
        if (current === s) current = null
        if (savePrompt?.session === s) savePrompt = null
    }

    // ------------------------------------------------- simpan password login

    fun requestSave(session: GameSession, password: String, register: Boolean) {
        if (savePrompt != null) return
        val old = store.password(session.server.address, session.username)
        if (old == password) return // sudah tersimpan dan sama: tidak perlu bertanya lagi
        savePrompt = SavePrompt(session, password, register, update = old != null)
    }

    fun confirmSave() {
        val p = savePrompt ?: return
        savePrompt = null
        val ok = store.savePassword(p.session.server.address, p.session.username, p.password)
        notice = if (ok) {
            "Tersimpan. Kamu akan masuk otomatis di ${p.session.server.name} sebagai ${p.session.username}."
        } else {
            "Password tidak bisa disimpan di HP ini. Kamu tetap bisa login manual."
        }
    }

    fun dismissSave() {
        savePrompt = null
    }

    fun forgetPassword(sv: ServerEntry, user: String) {
        store.deletePassword(sv.address, user)
    }

    override fun onCleared() {
        sessions.forEach { it.disconnect() }
    }
}

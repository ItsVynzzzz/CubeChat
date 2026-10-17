package com.example.cubechat

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.cubechat.data.AuthRules
import com.example.cubechat.data.ServerEntry
import com.example.cubechat.net.ChatLine
import com.example.cubechat.net.ChatText
import com.example.cubechat.net.Hud
import com.example.cubechat.net.Key
import com.example.cubechat.net.McClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Satu koneksi ke satu server dengan satu akun. Aplikasi bisa punya beberapa sesi aktif sekaligus;
 * setiap sesi punya klien (dunia, fisika, obrolan) sendiri.
 *
 * Login otomatis: kalau ada password tersimpan, sesi mengirim "/login <password>" begitu server meminta
 * login. Kalau pemain mengetik /login atau /register sendiri, sesi memberi tahu pemilik (AppViewModel)
 * supaya menanyakan apakah password mau disimpan.
 */
class GameSession(
    val id: Int,
    val server: ServerEntry,
    val username: String,
    app: Application,
    private val passwordProvider: () -> String?,
    private val onAuthCommand: (GameSession, String, Boolean) -> Unit
) : McClient.Listener {
    private val main = Handler(Looper.getMainLooper())
    private val client = McClient(app, this)

    val lines = mutableStateListOf<ChatLine>()
    var status by mutableStateOf("Menghubungkan...")
    var alive by mutableStateOf(true)
    var hud by mutableStateOf<Hud?>(null)
    var sneakOn by mutableStateOf(false)
    var sprintOn by mutableStateOf(false)
    var chatInput by mutableStateOf("")

    /** Pesan terakhir yang dikirim (terbaru di depan). Perintah login/daftar tidak dicatat. */
    val recentSent = mutableStateListOf<String>()

    // --- keadaan login otomatis
    private var autoPw: String? = null
    private var autoAttempts = 0
    private var promptSeen = false
    private var lastAutoAt = 0L
    private var spawnHandled = false
    private var failureSeen = false
    private var generation = 0 // callback tertunda dari koneksi lama diabaikan setelah sambung ulang

    fun start() = begin()

    fun reconnect() {
        note("Menyambung ulang...")
        begin()
    }

    private fun begin() {
        generation++
        alive = true
        status = "Menghubungkan..."
        hud = null
        autoPw = passwordProvider()
        autoAttempts = 0
        promptSeen = false
        lastAutoAt = 0L
        spawnHandled = false
        failureSeen = false
        client.setSneak(sneakOn)
        client.setSprint(sprintOn)
        client.connect(server.address, username)
    }

    fun disconnect() {
        generation++
        client.disconnect()
        alive = false
        status = "Terputus"
        hud = null
    }

    /** Dipanggil berkala oleh AppViewModel (di luar thread utama untuk mengambil data). */
    suspend fun refresh() {
        if (!alive) return
        val h = withContext(Dispatchers.Default) { client.hud() }
        hud = h
        if (h.spawned && !spawnHandled) {
            spawnHandled = true
            scheduleFallback()
        }
    }

    // ------------------------------------------------------------- obrolan

    fun send(text: String) {
        val cmd = AuthRules.parseCommand(text)
        if (cmd == null) {
            recentSent.remove(text)
            recentSent.add(0, text)
            while (recentSent.size > 5) recentSent.removeAt(recentSent.size - 1)
        }
        client.sendChat(text)
        if (cmd != null) {
            // Tunggu sebentar: kalau server menjawab "password salah", jangan tawarkan menyimpan.
            failureSeen = false
            val gen = generation
            main.postDelayed({
                if (gen == generation && alive && !failureSeen) onAuthCommand(this, cmd.password, cmd.register)
            }, 1500)
        }
    }

    private fun note(text: String) {
        lines.add(ChatText.text("[$text]", 0x9AA3AD))
    }

    private fun plain(l: ChatLine): String = l.spans.joinToString("") { it.text }

    private fun handleAuthText(text: String) {
        if (text.isBlank()) return
        if (AuthRules.isFailure(text)) {
            failureSeen = true
            if (lastAutoAt != 0L && System.currentTimeMillis() - lastAutoAt < 8000L && autoPw != null) {
                autoPw = null
                note("Password tersimpan sepertinya salah, login otomatis dihentikan. Ketik /login dengan password yang benar untuk memperbaruinya.")
            }
            return
        }
        if (AuthRules.isLoginPrompt(text)) {
            promptSeen = true
            sendAuto()
        }
    }

    private fun sendAuto() {
        val pw = autoPw ?: return
        if (autoAttempts >= 2) return
        autoAttempts++
        lastAutoAt = System.currentTimeMillis()
        val gen = generation
        main.postDelayed({
            if (gen == generation && alive) {
                client.sendChat("/login $pw")
                note("Login otomatis dikirim.")
            }
        }, 500)
    }

    /** Server yang hanya menampilkan petunjuk lewat judul layar: coba sekali setelah beberapa detik. */
    private fun scheduleFallback() {
        if (autoPw == null) return
        val gen = generation
        main.postDelayed({
            if (gen == generation && alive && !promptSeen && autoAttempts == 0) sendAuto()
        }, 7000)
    }

    // ------------------------------------------------------- McClient.Listener

    override fun onChat(line: ChatLine) {
        main.post {
            lines.add(line)
            if (lines.size > 500) lines.removeAt(0)
            handleAuthText(plain(line))
        }
    }

    override fun onNotice(text: String) {
        main.post { handleAuthText(text) }
    }

    override fun onStatus(text: String) {
        main.post { status = text }
    }

    override fun onDisconnected(reason: String) {
        main.post {
            alive = false
            status = reason
            hud = null
        }
    }

    // ----------------------------------------------------------------- kontrol

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
}

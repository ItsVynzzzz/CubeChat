package com.example.cubechat.data

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID

data class ServerEntry(val id: String, val name: String, val address: String, val defaultUser: String)

/** Penyimpanan kunci-nilai sederhana (di Android: SharedPreferences). */
interface KeyValue {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

/** Enkripsi password (di Android: Keystore, AES-GCM). */
interface Vault {
    fun encrypt(plain: String): String?
    fun decrypt(token: String): String?
}

/**
 * Daftar server, daftar akun, dan password login otomatis.
 * Password HANYA disimpan terenkripsi lewat [Vault]; teks aslinya tidak pernah ditulis ke penyimpanan.
 */
class Store(private val kv: KeyValue, private val vault: Vault) {
    private companion object {
        const val KEY_SERVERS = "servers_v1"
        const val KEY_ACCOUNTS = "accounts_v1"
        const val KEY_CREDS = "creds_v1"
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String): String = URLDecoder.decode(s, "UTF-8")

    private fun credKey(address: String, user: String) =
        address.trim().lowercase() + "|" + user.trim().lowercase()

    fun newId(): String = UUID.randomUUID().toString().take(8)

    // ------------------------------------------------------------------ server

    fun loadServers(): List<ServerEntry> {
        val raw = kv.get(KEY_SERVERS) ?: return migrateOld()
        return raw.split('\n').mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 4) null else ServerEntry(dec(p[0]), dec(p[1]), dec(p[2]), dec(p[3]))
        }
    }

    fun saveServers(list: List<ServerEntry>) {
        kv.put(KEY_SERVERS, list.joinToString("\n") { s ->
            listOf(s.id, s.name, s.address, s.defaultUser).joinToString("\t") { enc(it) }
        })
    }

    /** Versi lama menyimpan "recent" (alamat) dan "user". Jadikan daftar server pertama. */
    private fun migrateOld(): List<ServerEntry> {
        val user = kv.get("user")?.trim().orEmpty()
        val recent = kv.get("recent").orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        val list = recent.map { ServerEntry(newId(), it, it, user) }
        if (list.isNotEmpty()) saveServers(list)
        return list
    }

    // ------------------------------------------------------------------- akun

    fun loadAccounts(): List<String> {
        val raw = kv.get(KEY_ACCOUNTS)
        if (raw == null) {
            val old = kv.get("user")?.trim().orEmpty()
            if (old.isNotEmpty()) {
                saveAccounts(listOf(old))
                return listOf(old)
            }
            return emptyList()
        }
        return raw.split('\n').filter { it.isNotEmpty() }.map { dec(it) }
    }

    fun saveAccounts(list: List<String>) {
        kv.put(KEY_ACCOUNTS, list.joinToString("\n") { enc(it) })
    }

    // --------------------------------------------------------------- password

    private fun loadCreds(): MutableMap<String, String> {
        val map = LinkedHashMap<String, String>()
        val raw = kv.get(KEY_CREDS) ?: return map
        for (line in raw.split('\n')) {
            val p = line.split('\t')
            if (p.size == 2) map[dec(p[0])] = p[1]
        }
        return map
    }

    private fun saveCreds(map: Map<String, String>) {
        kv.put(KEY_CREDS, map.entries.joinToString("\n") { enc(it.key) + "\t" + it.value })
    }

    fun password(address: String, user: String): String? {
        val token = loadCreds()[credKey(address, user)] ?: return null
        return vault.decrypt(token)
    }

    fun hasPassword(address: String, user: String): Boolean =
        loadCreds().containsKey(credKey(address, user))

    /** Mengembalikan false kalau enkripsi gagal (password TIDAK disimpan). */
    fun savePassword(address: String, user: String, password: String): Boolean {
        val token = vault.encrypt(password) ?: return false
        val map = loadCreds()
        map[credKey(address, user)] = token
        saveCreds(map)
        return true
    }

    fun deletePassword(address: String, user: String) {
        val map = loadCreds()
        if (map.remove(credKey(address, user)) != null) saveCreds(map)
    }

    fun deleteForUser(user: String) {
        val suffix = "|" + user.trim().lowercase()
        val map = loadCreds()
        if (map.keys.removeAll { it.endsWith(suffix) }) saveCreds(map)
    }

    fun deleteForServer(address: String) {
        val prefix = address.trim().lowercase() + "|"
        val map = loadCreds()
        if (map.keys.removeAll { it.startsWith(prefix) }) saveCreds(map)
    }

    fun countForUser(user: String): Int {
        val suffix = "|" + user.trim().lowercase()
        return loadCreds().keys.count { it.endsWith(suffix) }
    }

    /** Alamat server berubah: pindahkan password yang sudah tersimpan. */
    fun moveServerPasswords(oldAddress: String, newAddress: String) {
        val o = oldAddress.trim().lowercase() + "|"
        val n = newAddress.trim().lowercase() + "|"
        if (o == n) return
        val map = loadCreds()
        val moved = LinkedHashMap<String, String>()
        for ((k, v) in map) moved[if (k.startsWith(o)) n + k.removePrefix(o) else k] = v
        saveCreds(moved)
    }
}

package com.example.cubechat.data

/** Perintah login/daftar yang diketik pemain. */
data class AuthCommand(val password: String, val register: Boolean)

/**
 * Aturan mengenali plugin login server (AuthMe, nLogin, LoginSecurity, dan sejenisnya).
 * Murni teks, tanpa dependensi Android, supaya mudah diuji.
 */
object AuthRules {
    private val LOGIN_CMDS = setOf("login", "log", "l")
    private val REGISTER_CMDS = setOf("register", "reg")

    // "/login", "/log", atau "/l" yang berdiri sendiri (bukan "/lobby", "/list", dst).
    private val loginMention = Regex("""/(login|log|l)(?=[\s<\[(.,:'"!]|$)""", RegexOption.IGNORE_CASE)
    private val registerMention = Regex("""/(register|reg)(?=[\s<\[(.,:'"!]|$)""", RegexOption.IGNORE_CASE)

    private val loginWords = listOf(
        "please login", "please log in", "please, login", "silakan login", "silahkan login",
        "silakan masuk", "silahkan masuk", "you must login", "you need to login", "harap login"
    )
    private val failureWords = listOf(
        "wrong password", "incorrect password", "invalid password", "password salah", "kata sandi salah",
        "sandi salah", "login failed", "failed to login", "gagal login", "salah!", "wrong!", "incorrect!",
        "tidak cocok", "doesn't match", "does not match", "not match"
    )
    private val successWords = listOf(
        "successful login", "successfully logged", "logged in", "login successful", "berhasil login",
        "berhasil masuk", "berhasil mendaftar", "successfully registered", "register successful",
        "welcome back", "selamat datang kembali", "authenticated", "terautentikasi"
    )

    /** "/login rahasia" atau "/register rahasia rahasia" -> AuthCommand. Selain itu null. */
    fun parseCommand(text: String): AuthCommand? {
        val t = text.trim()
        if (!t.startsWith("/")) return null
        val parts = t.split(Regex("\\s+"))
        if (parts.size < 2) return null
        // Buang awalan namespace, mis. "/authme:login".
        val cmd = parts[0].removePrefix("/").substringAfterLast(':').lowercase()
        return when {
            cmd in LOGIN_CMDS -> AuthCommand(parts[1], false)
            cmd in REGISTER_CMDS -> AuthCommand(parts[1], true)
            else -> null
        }
    }

    /** Server meminta login (menyebut /login atau kalimat yang sejenis). */
    fun isLoginPrompt(text: String): Boolean {
        val t = text.lowercase()
        if (isFailure(t)) return false
        if (loginMention.containsMatchIn(t)) return true
        // Server yang hanya menyebut /register tanpa /login sedang meminta daftar, bukan login.
        if (registerMention.containsMatchIn(t)) return false
        return loginWords.any { t.contains(it) }
    }

    fun isRegisterPrompt(text: String): Boolean {
        val t = text.lowercase()
        return registerMention.containsMatchIn(t) && !loginMention.containsMatchIn(t)
    }

    fun isFailure(text: String): Boolean {
        val t = text.lowercase()
        return failureWords.any { t.contains(it) }
    }

    fun isSuccess(text: String): Boolean {
        val t = text.lowercase()
        return successWords.any { t.contains(it) }
    }
}

# CubeChat (v0.1)

Klien chat Minecraft Java **1.21.11** untuk Android. Tanpa grafis: protokol lewat
[MCProtocolLib](https://github.com/GeyserMC/MCProtocolLib) + fisika sendiri
(gravitasi, tabrakan, air) supaya server melihat pemain yang "hidup".

Bukan produk resmi Minecraft dan tidak berafiliasi dengan Mojang/Microsoft.

## Cara build (GitHub Actions)
1. Buat repo baru, upload isi folder ini (termasuk `.github/`).
2. Tab **Actions** -> workflow **Build APK** jalan otomatis (atau Run workflow).
3. Selesai: unduh artifact **CubeChat-debug-apk**. Kalau gagal, unduh artifact **build-log**.

Workflow mengunduh jar server 1.21.11 di runner hanya untuk membuat
`blockdata.txt` (ID block state untuk fisika). Jar itu tidak masuk repo maupun APK.

## Yang sudah ada
- Connect (mode offline), terima chat (system / player / disguised), kirim chat + `/perintah`
- Teleport confirm (termasuk flag relatif), `PlayerLoaded`, auto-respawn
- Parse chunk, update blok, gravitasi + tabrakan AABB 0,6x1,8, air, knockback dari server

## Belum ada
- Login Microsoft (server online-mode) dan chat signing
- Resource pack prompt, SRV record
- Bentuk blok parsial (slab, tangga, pagar) dianggap kubus penuh
- Foreground service (koneksi putus kalau app dimatikan Android)

package com.example.cubechat.net

import android.content.Context
import com.example.cubechat.world.BlockData
import com.example.cubechat.world.Physics
import com.example.cubechat.world.PlayerState
import com.example.cubechat.world.World
import io.netty.buffer.Unpooled
import net.kyori.adventure.text.Component
import org.geysermc.mcprotocollib.auth.GameProfile
import org.geysermc.mcprotocollib.network.ClientSession
import org.geysermc.mcprotocollib.network.Session
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory
import org.geysermc.mcprotocollib.network.packet.Packet
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol
import org.geysermc.mcprotocollib.protocol.codec.MinecraftTypes
import org.geysermc.mcprotocollib.protocol.data.UnexpectedEncryptionException
import org.geysermc.mcprotocollib.protocol.data.game.ClientCommand
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.*
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.*
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.*
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.*
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.*
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.*
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.*
import java.net.InetSocketAddress
import java.util.BitSet
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.floor

/**
 * Klien Minecraft 1.21.11 tanpa grafis: protokol (MCProtocolLib) + fisika sendiri.
 * SEMUA pemanggilan MCProtocolLib ada di file ini. Kalau build gagal karena nama
 * kelas/parameter paket, perbaikannya cukup di sini.
 */
class McClient(context: Context, private val listener: Listener) {

    private companion object {
        // Hanya dipakai MCPL untuk lebar palet global saat MENULIS; untuk membaca (get) tidak berpengaruh.
        const val BLOCK_STATE_REGISTRY_SIZE = 32768
        const val BIOME_REGISTRY_SIZE = 128
    }

    interface Listener {
        fun onChat(line: ChatLine)
        fun onStatus(text: String)
        fun onDisconnected(reason: String)
    }

    private val world = World()
    private val physics = Physics(world)
    private val state = PlayerState()
    private val lock = Any()

    @Volatile private var session: ClientSession? = null
    private var ticker: ScheduledExecutorService? = null
    private var entityId = -1
    private var worldName = ""
    @Volatile private var spawned = false
    private var loadedSent = false
    private var lastX = 0.0
    private var lastY = 0.0
    private var lastZ = 0.0
    private var lastOnGround = false
    private var posReminder = 0
    private var tickCount = 0L

    init {
        BlockData.load(context.applicationContext)
    }

    // ---------------------------------------------------------------- koneksi

    fun connect(address: String, username: String) {
        disconnect()
        val host = address.substringBefore(':')
        val port = address.substringAfter(':', "25565").toIntOrNull() ?: 25565
        resetState()
        thread(name = "mc-connect") {
            try {
                // Mode offline: untuk server offline-mode atau yang tidak mewajibkan login Microsoft.
                val uuid = UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(Charsets.UTF_8))
                val protocol = MinecraftProtocol(GameProfile(uuid, username), null)
                val s = ClientNetworkSessionFactory.factory()
                    .setRemoteSocketAddress(InetSocketAddress(host, port))
                    .setProtocol(protocol)
                    .create()
                s.addListener(object : SessionAdapter() {
                    override fun packetReceived(session: Session, packet: Packet) {
                        handle(session, packet)
                    }

                    override fun disconnected(event: DisconnectedEvent) {
                        val why = if (event.cause is UnexpectedEncryptionException) {
                            "server mewajibkan login Microsoft (online-mode); v0.1 hanya untuk server offline-mode"
                        } else {
                            ChatText.plain(event.reason)
                        }
                        stopTicker()
                        spawned = false
                        listener.onDisconnected("Terputus: $why")
                    }
                })
                session = s
                listener.onStatus("Menghubungkan ke $host:$port ...")
                s.connect()
                startTicker()
            } catch (e: Throwable) {
                val root = generateSequence(e) { it.cause }.last()
                listener.onDisconnected("Gagal: ${e.javaClass.simpleName} / ${root.javaClass.simpleName}: ${root.message}")
            }
        }
    }

    fun disconnect() {
        stopTicker()
        spawned = false
        val s = session
        session = null
        try {
            s?.disconnect(Component.text("Disconnected"))
        } catch (e: Exception) {
        }
    }

    private fun resetState() {
        synchronized(lock) {
            world.clear()
            worldName = ""
            entityId = -1
            loadedSent = false
            posReminder = 0
            tickCount = 0
            lastOnGround = false
        }
    }

    // ----------------------------------------------------------------- kirim

    fun sendChat(text: String) {
        val s = session ?: return
        if (text.startsWith("/")) {
            s.send(ServerboundChatCommandPacket(text.substring(1)))
        } else {
            // Pesan TIDAK bertanda tangan: cocok untuk offline-mode atau server dengan
            // enforce-secure-profile=false. Parameter: message, timeStamp(ms), salt, signature,
            // offset, acknowledgedMessages, checksum (0 = tidak dihitung).
            s.send(ServerboundChatPacket(text, System.currentTimeMillis(), 0L, null, 0, BitSet(), 0))
        }
    }

    // -------------------------------------------------------------- terima

    private fun handle(s: Session, p: Packet) {
        try {
            when (p) {
                is ClientboundLoginPacket -> {
                    entityId = p.entityId
                    applyDimension(p.commonPlayerSpawnInfo.worldName.asString())
                    listener.onStatus("Masuk server (entity $entityId)")
                }
                is ClientboundRespawnPacket -> {
                    applyDimension(p.commonPlayerSpawnInfo.worldName.asString())
                    spawned = false
                }
                is ClientboundPlayerPositionPacket -> onTeleport(s, p)
                is ClientboundLevelChunkWithLightPacket -> onChunk(p)
                is ClientboundBlockUpdatePacket -> {
                    val e = p.entry
                    world.setBlock(e.position.x, e.position.y, e.position.z, e.block)
                }
                is ClientboundSectionBlocksUpdatePacket -> {
                    for (e in p.entries) world.setBlock(e.position.x, e.position.y, e.position.z, e.block)
                }
                is ClientboundSetEntityMotionPacket -> {
                    if (p.entityId == entityId) synchronized(lock) {
                        state.vx = p.movement.x
                        state.vy = p.movement.y
                        state.vz = p.movement.z
                    }
                }
                is ClientboundSetHealthPacket -> {
                    if (p.health <= 0f) s.send(ServerboundClientCommandPacket(ClientCommand.RESPAWN))
                }
                is ClientboundSystemChatPacket -> {
                    if (!p.isOverlay) listener.onChat(ChatText.fromComponent(p.content))
                }
                is ClientboundPlayerChatPacket -> {
                    val body = p.unsignedContent ?: Component.text(p.content)
                    listener.onChat(ChatText.prefixed("<" + ChatText.plain(p.name) + "> ", body))
                }
                is ClientboundDisguisedChatPacket -> {
                    listener.onChat(ChatText.prefixed("<" + ChatText.plain(p.name) + "> ", p.message))
                }
            }
        } catch (e: Exception) {
            listener.onStatus("Error ${p.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun applyDimension(name: String) {
        synchronized(lock) {
            if (name == worldName) return
            worldName = name
            world.clear()
            // Nether dan End: y 0..255. Selain itu dianggap overworld: y -64..319.
            val flat = name.contains("nether") || name.contains("the_end")
            world.minY = if (flat) 0 else -64
            world.height = if (flat) 256 else 384
        }
    }

    private fun onTeleport(s: Session, p: ClientboundPlayerPositionPacket) {
        synchronized(lock) {
            // Flag relatif (PositionElement): komponen yang ditandai dihitung dari nilai sekarang.
            val rel = p.relatives
            state.x = if (PositionElement.X in rel) state.x + p.position.x else p.position.x
            state.y = if (PositionElement.Y in rel) state.y + p.position.y else p.position.y
            state.z = if (PositionElement.Z in rel) state.z + p.position.z else p.position.z
            state.vx = if (PositionElement.DELTA_X in rel) state.vx + p.deltaMovement.x else p.deltaMovement.x
            state.vy = if (PositionElement.DELTA_Y in rel) state.vy + p.deltaMovement.y else p.deltaMovement.y
            state.vz = if (PositionElement.DELTA_Z in rel) state.vz + p.deltaMovement.z else p.deltaMovement.z
            state.yaw = if (PositionElement.Y_ROT in rel) state.yaw + p.yRot else p.yRot
            state.pitch = if (PositionElement.X_ROT in rel) state.pitch + p.xRot else p.xRot
            state.onGround = false
            lastX = state.x; lastY = state.y; lastZ = state.z
            spawned = true
        }
        s.send(ServerboundAcceptTeleportationPacket(p.id))
        synchronized(lock) { sendPosRot(s) }
    }

    private fun onChunk(p: ClientboundLevelChunkWithLightPacket) {
        val count = world.height shr 4
        val sections = arrayOfNulls<CharArray>(count)
        val buf = Unpooled.wrappedBuffer(p.chunkData)
        try {
            for (i in 0 until count) {
                if (!buf.isReadable) break
                val sec = MinecraftTypes.readChunkSection(buf, BLOCK_STATE_REGISTRY_SIZE, BIOME_REGISTRY_SIZE)
                if (sec.blockCount == 0) continue
                val arr = CharArray(4096)
                var n = 0
                for (y in 0 until 16) for (z in 0 until 16) for (x in 0 until 16)
                    arr[n++] = sec.getBlock(x, y, z).toChar()
                sections[i] = arr
            }
        } finally {
            buf.release()
        }
        world.putChunk(p.x, p.z, World.Chunk(sections))
    }

    // ----------------------------------------------------------------- tick

    private fun startTicker() {
        stopTicker()
        ticker = Executors.newSingleThreadScheduledExecutor().also {
            it.scheduleAtFixedRate(Runnable {
                try { tick() } catch (e: Exception) { }
            }, 50L, 50L, TimeUnit.MILLISECONDS)
        }
    }

    private fun stopTicker() {
        ticker?.shutdownNow()
        ticker = null
    }

    private fun tick() {
        val s = session ?: return
        if (!spawned) return
        synchronized(lock) {
            if (!physics.tick(state)) return // chunk belum ada: belum bisa simulasi
            tickCount++

            if (!loadedSent) {
                loadedSent = true
                s.send(ServerboundPlayerLoadedPacket.INSTANCE) // 1.21.6+: "client sudah selesai memuat"
            }

            val dx = state.x - lastX
            val dy = state.y - lastY
            val dz = state.z - lastZ
            posReminder++
            if (dx * dx + dy * dy + dz * dz > 4.0E-8 || posReminder >= 20) {
                sendPosRot(s)
            } else if (state.onGround != lastOnGround) {
                s.send(ServerboundMovePlayerStatusOnlyPacket(state.onGround, state.horizontalCollision))
            }
            lastOnGround = state.onGround

            if (tickCount % 100 == 0L) {
                world.prune(floor(state.x).toInt() shr 4, floor(state.z).toInt() shr 4, 5)
            }
        }
    }

    /** Harus dipanggil di dalam synchronized(lock). */
    private fun sendPosRot(s: Session) {
        s.send(
            ServerboundMovePlayerPosRotPacket(
                state.onGround, state.horizontalCollision,
                state.x, state.y, state.z, state.yaw, state.pitch
            )
        )
        lastX = state.x; lastY = state.y; lastZ = state.z
        lastOnGround = state.onGround
        posReminder = 0
    }

    // ------------------------------------------------------------------ HUD

    fun hudText(): String = synchronized(lock) {
        "x=%.2f y=%.2f z=%.2f  %s  chunk=%d  blockdata=%s".format(
            state.x, state.y, state.z,
            if (state.onGround) "di tanah" else "melayang",
            world.chunkCount(),
            if (BlockData.loaded) "ok" else "HILANG"
        )
    }
}

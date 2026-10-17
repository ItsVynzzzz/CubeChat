package com.example.cubechat.net

import android.content.Context
import com.example.cubechat.world.Abilities
import com.example.cubechat.world.BlockData
import com.example.cubechat.world.InputState
import com.example.cubechat.world.ItemData
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
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntryAction
import org.geysermc.mcprotocollib.protocol.data.game.ResourcePackStatus
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandNode
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandType
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.HandPreference
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType
import org.geysermc.mcprotocollib.protocol.data.game.item.HashedStack
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.GameEvent
import org.geysermc.mcprotocollib.protocol.data.game.setting.ChatVisibility
import org.geysermc.mcprotocollib.protocol.data.game.setting.ParticleStatus
import org.geysermc.mcprotocollib.protocol.data.game.setting.SkinPart
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundPingPacket
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundResourcePackPushPacket
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundClientInformationPacket
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundPongPacket
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundResourcePackPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.*
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.*
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.*
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.*
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.*
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.*
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.*
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.*
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
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerState as McPlayerState

// ------------------------------------------------------------ model untuk UI

data class ItemView(val name: String, val count: Int)
data class MenuView(val id: Int, val title: String, val slots: List<ItemView?>)
data class PlayerEntry(val name: String, val ping: Int, val mode: String)
data class CommandInfo(val name: String, val usage: String)

/** Tombol gerak yang bisa ditekan dari UI. */
enum class Key { FORWARD, BACK, LEFT, RIGHT, JUMP }

/** Foto keadaan klien untuk UI (dibuat ulang beberapa kali per detik). */
data class Hud(
    val x: Double, val y: Double, val z: Double,
    val yaw: Float, val pitch: Float,
    val onGround: Boolean, val flying: Boolean, val canFly: Boolean,
    val sprintOn: Boolean, val sneakOn: Boolean,
    val health: Float, val food: Int,
    val gameMode: String, val dimension: String, val dayTime: Long,
    val selectedSlot: Int, val inventory: List<ItemView?>,
    val menu: MenuView?,
    val title: ChatLine?, val subtitle: ChatLine?, val actionBar: ChatLine?,
    val header: ChatLine?, val footer: ChatLine?,
    val players: List<PlayerEntry>, val commands: List<CommandInfo>,
    val chunkCount: Int, val dead: Boolean, val spawned: Boolean
)

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

        // Jarak pandang yang diminta ke server (chunk). Kecil = hemat data & memori di HP.
        const val RENDER_DISTANCE = 6
        const val PRUNE_RADIUS = 8
    }

    interface Listener {
        fun onChat(line: ChatLine)
        fun onStatus(text: String)
        fun onDisconnected(reason: String)

        /** Teks dari judul layar / action bar (bukan chat), mis. petunjuk login dari server. */
        fun onNotice(text: String) {}
    }

    private val world = World()
    private val physics = Physics(world)
    private val state = PlayerState()
    private val input = InputState()
    private val abilities = Abilities()
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
    private var lastYaw = 0f
    private var lastPitch = 0f
    private var lastOnGround = false
    private var lastInputSig = 0
    private var lastSprint = false
    private var posReminder = 0
    private var tickCount = 0L
    private var sequence = 0
    @Volatile private var lastTickError: String? = null

    // keadaan yang ditampilkan di UI (semua diakses di dalam synchronized(lock))
    private var health = 20f
    private var food = 20
    private var gameModeName = "SURVIVAL"
    private var dayTime = 0L
    private var selectedSlot = 0
    private var inventory = ArrayList<ItemView?>()
    private var menuId = -1
    private var menuTitle = ""
    private var menuItems = ArrayList<ItemView?>()
    private var menuState = 0
    private var menuStateInv = 0
    private var titleLine: ChatLine? = null
    private var subtitleLine: ChatLine? = null
    private var titleUntil = 0L
    private var actionLine: ChatLine? = null
    private var actionUntil = 0L
    private var fadeIn = 10
    private var stay = 70
    private var fadeOut = 20
    private var header: ChatLine? = null
    private var footer: ChatLine? = null
    private val players = LinkedHashMap<UUID, PlayerEntry>()
    private var commands: List<CommandInfo> = emptyList()

    init {
        // Android tidak punya transport native netty (epoll/kqueue/io_uring): pakai NIO.
        System.setProperty("Mcpl.disable-native-transport", "true")
        BlockData.load(context.applicationContext)
        ItemData.load(context.applicationContext)
        resetState()
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

    /** Dipanggil dari thread UI: pemutusan koneksi jaringan dilakukan di thread lain supaya UI tidak macet. */
    fun disconnect() {
        stopTicker()
        spawned = false
        val s = session
        session = null
        if (s != null) {
            thread(name = "mc-disconnect") {
                try {
                    s.disconnect(Component.text("Disconnected"))
                } catch (e: Throwable) {
                }
            }
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
            lastInputSig = 0
            lastSprint = false
            sequence = 0
            health = 20f
            food = 20
            gameModeName = "SURVIVAL"
            dayTime = 0L
            selectedSlot = 0
            inventory = ArrayList<ItemView?>().also { l -> repeat(46) { l.add(null) } }
            menuId = -1
            menuItems = ArrayList()
            titleLine = null
            subtitleLine = null
            actionLine = null
            titleUntil = 0L
            actionUntil = 0L
            header = null
            footer = null
            players.clear()
            commands = emptyList()
            state.vx = 0.0; state.vy = 0.0; state.vz = 0.0
            state.flying = false
            state.sprinting = false
            state.sneaking = false
            state.jumpDelay = 0
            abilities.canFly = false
            abilities.noClip = false
            abilities.walkSpeed = 0.1f
            abilities.flySpeed = 0.05f
            input.forward = false; input.back = false; input.left = false; input.right = false
            input.jump = false
            // sneak/sprint toggle dari UI sengaja dipertahankan
        }
    }

    // ------------------------------------------------------------- kontrol UI

    fun press(k: Key, down: Boolean) {
        synchronized(lock) {
            when (k) {
                Key.FORWARD -> input.forward = down
                Key.BACK -> input.back = down
                Key.LEFT -> input.left = down
                Key.RIGHT -> input.right = down
                Key.JUMP -> input.jump = down
            }
        }
    }

    fun setSneak(on: Boolean) {
        synchronized(lock) { input.sneak = on }
    }

    fun setSprint(on: Boolean) {
        synchronized(lock) { input.sprint = on }
    }

    fun look(dYaw: Float, dPitch: Float) {
        synchronized(lock) {
            state.yaw = wrapYaw(state.yaw + dYaw)
            state.pitch = (state.pitch + dPitch).coerceIn(-90f, 90f)
        }
    }

    fun faceYaw(yaw: Float) {
        synchronized(lock) { state.yaw = wrapYaw(yaw) }
    }

    private fun wrapYaw(y: Float): Float {
        var v = y % 360f
        if (v >= 180f) v -= 360f
        if (v < -180f) v += 360f
        return v
    }

    /** Terbang hidup/mati (hanya kalau server mengizinkan, mis. mode creative). */
    fun toggleFly() {
        val s = session ?: return
        val nowFlying = synchronized(lock) {
            if (!abilities.canFly || abilities.noClip) return
            state.flying = !state.flying
            state.flying
        }
        s.send(ServerboundPlayerAbilitiesPacket(nowFlying))
    }

    fun selectSlot(slot: Int) {
        val s = session ?: return
        if (slot !in 0..8) return
        synchronized(lock) { selectedSlot = slot }
        s.send(ServerboundSetCarriedItemPacket(slot))
    }

    /** Klik kanan di udara dengan item di tangan (membuka menu compass, minum, makan, dll). */
    fun useItem() {
        val s = session ?: return
        val (yaw, pitch, seq) = synchronized(lock) { Triple(state.yaw, state.pitch, ++sequence) }
        s.send(ServerboundUseItemPacket(Hand.MAIN_HAND, seq, yaw, pitch))
        s.send(ServerboundSwingPacket(Hand.MAIN_HAND))
    }

    fun swing() {
        val s = session ?: return
        s.send(ServerboundSwingPacket(Hand.MAIN_HAND))
    }

    fun clickMenuSlot(slot: Int) {
        val s = session ?: return
        val (id, st) = synchronized(lock) { Pair(menuId, menuState) }
        if (id < 0) return
        s.send(
            ServerboundContainerClickPacket(
                id, st, slot, ContainerActionType.CLICK_ITEM, ClickItemAction.LEFT_CLICK,
                null, emptyMap<Int, HashedStack?>()
            )
        )
    }

    fun closeMenu() {
        val s = session ?: return
        val id = synchronized(lock) {
            val v = menuId
            menuId = -1
            v
        }
        if (id >= 0) s.send(ServerboundContainerClosePacket(id))
    }

    fun respawn() {
        session?.send(ServerboundClientCommandPacket(ClientCommand.RESPAWN))
    }

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
                is ClientboundLoginPacket -> onLogin(s, p)
                is ClientboundRespawnPacket -> {
                    val info = p.commonPlayerSpawnInfo
                    applyDimension(info.worldName.asString())
                    synchronized(lock) {
                        setGameMode(info.gameMode)
                        menuId = -1
                    }
                    spawned = false
                }
                is ClientboundPlayerPositionPacket -> onTeleport(s, p)
                is ClientboundPlayerRotationPacket -> synchronized(lock) {
                    state.yaw = if (p.isRelativeY) state.yaw + p.yRot else p.yRot
                    state.pitch = if (p.isRelativeX) state.pitch + p.xRot else p.xRot
                }
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
                is ClientboundExplodePacket -> {
                    val k = p.playerKnockback
                    if (k != null) synchronized(lock) {
                        state.vx += k.x
                        state.vy += k.y
                        state.vz += k.z
                    }
                }
                is ClientboundPlayerAbilitiesPacket -> synchronized(lock) {
                    abilities.canFly = p.isCanFly
                    abilities.flySpeed = p.flySpeed
                    abilities.walkSpeed = p.walkSpeed
                    state.flying = p.isFlying && p.isCanFly
                }
                is ClientboundSetHeldSlotPacket -> synchronized(lock) { selectedSlot = p.slot.coerceIn(0, 8) }
                is ClientboundGameEventPacket -> {
                    if (p.notification == GameEvent.CHANGE_GAME_MODE) {
                        val gm = p.value as? GameMode
                        if (gm != null) synchronized(lock) { setGameMode(gm) }
                    }
                }
                is ClientboundSetTimePacket -> synchronized(lock) { dayTime = p.dayTime }
                is ClientboundSetHealthPacket -> {
                    synchronized(lock) {
                        health = p.health
                        food = p.food
                    }
                    if (p.health <= 0f) s.send(ServerboundClientCommandPacket(ClientCommand.RESPAWN))
                }

                // --- chat, judul, tab list
                is ClientboundSystemChatPacket -> {
                    if (p.isOverlay) setActionBar(ChatText.fromComponent(p.content))
                    else listener.onChat(ChatText.fromComponent(p.content))
                }
                is ClientboundPlayerChatPacket -> {
                    val body = p.unsignedContent ?: Component.text(p.content)
                    listener.onChat(ChatText.prefixed("<" + ChatText.plain(p.name) + "> ", body))
                }
                is ClientboundDisguisedChatPacket -> {
                    listener.onChat(ChatText.prefixed("<" + ChatText.plain(p.name) + "> ", p.message))
                }
                is ClientboundSetActionBarTextPacket -> {
                    val t = p.text
                    if (t != null) setActionBar(ChatText.fromComponent(t))
                }
                is ClientboundSetTitleTextPacket -> {
                    val line = ChatText.fromComponent(p.text)
                    synchronized(lock) {
                        titleLine = line
                        titleUntil = System.currentTimeMillis() + titleMillis()
                    }
                    listener.onNotice(plainOf(line))
                }
                is ClientboundSetSubtitleTextPacket -> {
                    val t = p.text
                    val line = if (t != null) ChatText.fromComponent(t) else null
                    synchronized(lock) {
                        subtitleLine = line
                        titleUntil = System.currentTimeMillis() + titleMillis()
                    }
                    if (line != null) listener.onNotice(plainOf(line))
                }
                is ClientboundSetTitlesAnimationPacket -> synchronized(lock) {
                    fadeIn = p.fadeIn
                    stay = p.stay
                    fadeOut = p.fadeOut
                }
                is ClientboundClearTitlesPacket -> synchronized(lock) {
                    titleLine = null
                    subtitleLine = null
                    titleUntil = 0L
                }
                is ClientboundTabListPacket -> synchronized(lock) {
                    header = ChatText.fromComponent(p.header)
                    footer = ChatText.fromComponent(p.footer)
                }
                is ClientboundPlayerInfoUpdatePacket -> onPlayerInfo(p)
                is ClientboundPlayerInfoRemovePacket -> synchronized(lock) {
                    for (id in p.profileIds) players.remove(id)
                }

                // --- inventori dan menu server
                is ClientboundContainerSetContentPacket -> synchronized(lock) {
                    val list = ArrayList<ItemView?>(p.items.size)
                    for (stack in p.items) list.add(view(stack))
                    if (p.containerId == 0) {
                        while (list.size < 46) list.add(null)
                        inventory = list
                        menuStateInv = p.stateId
                    } else if (p.containerId == menuId) {
                        menuItems = list
                        menuState = p.stateId
                    }
                }
                is ClientboundContainerSetSlotPacket -> synchronized(lock) {
                    if (p.containerId == 0) {
                        if (p.slot in inventory.indices) inventory[p.slot] = view(p.item)
                        menuStateInv = p.stateId
                    } else if (p.containerId == menuId) {
                        if (p.slot in menuItems.indices) menuItems[p.slot] = view(p.item)
                        menuState = p.stateId
                    }
                }
                is ClientboundOpenScreenPacket -> synchronized(lock) {
                    menuId = p.containerId
                    menuTitle = ChatText.plain(p.title)
                    menuItems = ArrayList()
                    menuState = 0
                }
                is ClientboundContainerClosePacket -> synchronized(lock) {
                    if (p.containerId == menuId) menuId = -1
                }

                // --- perintah server
                is ClientboundCommandsPacket -> {
                    val list = buildCommands(p)
                    synchronized(lock) { commands = list }
                }

                // --- balasan wajib supaya server tidak menendang kita
                is ClientboundResourcePackPushPacket -> {
                    s.send(ServerboundResourcePackPacket(p.id, ResourcePackStatus.ACCEPTED))
                    s.send(ServerboundResourcePackPacket(p.id, ResourcePackStatus.DOWNLOADED))
                    s.send(ServerboundResourcePackPacket(p.id, ResourcePackStatus.SUCCESSFULLY_LOADED))
                    listener.onChat(ChatText.text("[resource pack diterima otomatis, tidak diunduh]", 0xAAAAAA))
                }
                is ClientboundPingPacket -> s.send(ServerboundPongPacket(p.id))
            }
        } catch (e: Throwable) {
            listener.onStatus("Error ${p.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun onLogin(s: Session, p: ClientboundLoginPacket) {
        val info = p.commonPlayerSpawnInfo
        entityId = p.entityId
        applyDimension(info.worldName.asString())
        synchronized(lock) {
            setGameMode(info.gameMode)
            menuId = -1
        }
        // Tanpa paket ini server memakai jarak pandang bawaan yang sangat kecil.
        s.send(
            ServerboundClientInformationPacket(
                "en_us", RENDER_DISTANCE, ChatVisibility.FULL, true,
                SkinPart.values().toList(), HandPreference.RIGHT_HAND, false, true, ParticleStatus.MINIMAL
            )
        )
        listener.onStatus("Masuk server (entity $entityId)")
    }

    /** Harus dipanggil di dalam synchronized(lock). */
    private fun setGameMode(gm: GameMode) {
        gameModeName = gm.name
        abilities.noClip = gm == GameMode.SPECTATOR
    }

    private fun titleMillis(): Long = (fadeIn + stay + fadeOut) * 50L

    private fun setActionBar(line: ChatLine) {
        synchronized(lock) {
            actionLine = line
            actionUntil = System.currentTimeMillis() + 3500L
        }
        listener.onNotice(plainOf(line))
    }

    private fun plainOf(line: ChatLine): String = line.spans.joinToString("") { it.text }

    private fun onPlayerInfo(p: ClientboundPlayerInfoUpdatePacket) {
        synchronized(lock) {
            val add = PlayerListEntryAction.ADD_PLAYER in p.actions
            val latency = add || PlayerListEntryAction.UPDATE_LATENCY in p.actions
            val mode = add || PlayerListEntryAction.UPDATE_GAME_MODE in p.actions
            for (e in p.entries) {
                val old = players[e.profileId]
                val name = e.profile?.name ?: old?.name ?: "?"
                val ping = if (latency) e.latency else old?.ping ?: 0
                val gm = if (mode) (e.gameMode?.name ?: "") else old?.mode ?: ""
                players[e.profileId] = PlayerEntry(name, ping, gm)
            }
        }
    }

    private fun view(st: ItemStack?): ItemView? {
        if (st == null || st.amount <= 0) return null
        val comps = st.dataComponentsPatch
        val custom = comps?.get(DataComponentTypes.CUSTOM_NAME) ?: comps?.get(DataComponentTypes.ITEM_NAME)
        val fallback = ItemData.nameOf(st.id)
        val name = if (custom != null) ChatText.plain(custom).ifBlank { fallback } else fallback
        return ItemView(name, st.amount)
    }

    private fun buildCommands(p: ClientboundCommandsPacket): List<CommandInfo> {
        val nodes = p.nodes
        if (p.firstNodeIndex !in nodes.indices) return emptyList()
        val root = nodes[p.firstNodeIndex]
        val out = ArrayList<CommandInfo>()
        for (ci in root.childIndices) {
            val node = nodes.getOrNull(ci) ?: continue
            val name = node.name ?: continue
            out.add(CommandInfo(name, usageOf(nodes, node)))
        }
        out.sortBy { it.name }
        return out
    }

    /** Ringkasan pilihan setelah nama perintah, mis. "survival | creative | adventure". */
    private fun usageOf(nodes: Array<CommandNode>, node: CommandNode): String {
        val parts = ArrayList<String>()
        for (ci in node.childIndices) {
            val child = nodes.getOrNull(ci) ?: continue
            val n = child.name ?: continue
            parts.add(if (child.type == CommandType.ARGUMENT) "<$n>" else n)
        }
        val text = parts.joinToString(" | ")
        return if (text.length > 90) text.substring(0, 90) + "..." else text
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
            lastYaw = state.yaw; lastPitch = state.pitch
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
                try {
                    tick()
                } catch (e: Throwable) {
                    // Jangan matikan ticker; laporkan sekali per jenis error supaya tidak membanjiri UI.
                    val msg = "Error tick: ${e.javaClass.simpleName}: ${e.message}"
                    if (msg != lastTickError) {
                        lastTickError = msg
                        listener.onStatus(msg)
                    }
                }
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
            if (state.flying && !abilities.canFly && !abilities.noClip) state.flying = false
            if (!physics.tick(state, input, abilities)) return // chunk belum ada: belum bisa simulasi
            tickCount++

            if (!loadedSent) {
                loadedSent = true
                s.send(ServerboundPlayerLoadedPacket.INSTANCE) // 1.21.6+: "client sudah selesai memuat"
            }

            // Mendarat saat terbang = berhenti terbang (seperti vanilla).
            if (state.flying && state.onGround && !abilities.noClip) {
                state.flying = false
                s.send(ServerboundPlayerAbilitiesPacket(false))
            }

            // Input (WASD, lompat, jongkok, lari) dikirim hanya saat berubah.
            val sprintNow = state.sprinting
            val sig = (if (input.forward) 1 else 0) or (if (input.back) 2 else 0) or
                (if (input.left) 4 else 0) or (if (input.right) 8 else 0) or
                (if (input.jump) 16 else 0) or (if (input.sneak) 32 else 0) or
                (if (sprintNow) 64 else 0)
            if (sig != lastInputSig) {
                lastInputSig = sig
                s.send(
                    ServerboundPlayerInputPacket(
                        input.forward, input.back, input.left, input.right,
                        input.jump, input.sneak, sprintNow
                    )
                )
            }
            if (sprintNow != lastSprint) {
                lastSprint = sprintNow
                s.send(
                    ServerboundPlayerCommandPacket(
                        entityId,
                        if (sprintNow) McPlayerState.START_SPRINTING else McPlayerState.STOP_SPRINTING,
                        0
                    )
                )
            }

            val dx = state.x - lastX
            val dy = state.y - lastY
            val dz = state.z - lastZ
            posReminder++
            if (dx * dx + dy * dy + dz * dz > 4.0E-8 || posReminder >= 20) {
                sendPosRot(s)
            } else if (state.yaw != lastYaw || state.pitch != lastPitch) {
                s.send(ServerboundMovePlayerRotPacket(state.onGround, state.horizontalCollision, state.yaw, state.pitch))
                lastYaw = state.yaw; lastPitch = state.pitch
                lastOnGround = state.onGround
            } else if (state.onGround != lastOnGround) {
                s.send(ServerboundMovePlayerStatusOnlyPacket(state.onGround, state.horizontalCollision))
            }
            lastOnGround = state.onGround

            if (tickCount % 100 == 0L) {
                world.prune(floor(state.x).toInt() shr 4, floor(state.z).toInt() shr 4, PRUNE_RADIUS)
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
        lastYaw = state.yaw; lastPitch = state.pitch
        lastOnGround = state.onGround
        posReminder = 0
    }

    // ------------------------------------------------------------------ HUD

    fun hud(): Hud = synchronized(lock) {
        val now = System.currentTimeMillis()
        val titleOn = now < titleUntil
        Hud(
            x = state.x, y = state.y, z = state.z,
            yaw = state.yaw, pitch = state.pitch,
            onGround = state.onGround, flying = state.flying, canFly = abilities.canFly,
            sprintOn = input.sprint, sneakOn = input.sneak,
            health = health, food = food,
            gameMode = gameModeName, dimension = worldName, dayTime = dayTime,
            selectedSlot = selectedSlot, inventory = ArrayList(inventory),
            menu = if (menuId >= 0) MenuView(menuId, menuTitle, ArrayList(menuItems)) else null,
            title = if (titleOn) titleLine else null,
            subtitle = if (titleOn) subtitleLine else null,
            actionBar = if (now < actionUntil) actionLine else null,
            header = header, footer = footer,
            players = players.values.sortedBy { it.name.lowercase() },
            commands = commands,
            chunkCount = world.chunkCount(), dead = health <= 0f, spawned = spawned
        )
    }
}

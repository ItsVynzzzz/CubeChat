package com.example.cubechat.world

import android.content.Context
import java.util.BitSet
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.floor

/**
 * Data blok untuk fisika. File assets/blockdata.txt dibuat saat build oleh
 * tools/gen_blockdata.py dari data resmi Minecraft 1.21.11.
 * Format: "N a-b" = block state tanpa collision, "F a-b" = fluida, "L a-b" = bisa dipanjat.
 */
object BlockData {
    private val nonSolid = BitSet().apply { set(0) } // id 0 = air
    private val fluid = BitSet()
    private val climbable = BitSet()

    @Volatile
    var loaded = false
        private set

    fun load(context: Context) {
        if (loaded) return
        try {
            context.assets.open("blockdata.txt").bufferedReader().useLines { lines ->
                for (line in lines) {
                    val p = line.trim().split(' ')
                    if (p.size != 2) continue
                    val r = p[1].split('-')
                    val a = r[0].toInt()
                    val b = (if (r.size > 1) r[1] else r[0]).toInt()
                    when (p[0]) {
                        "N" -> nonSolid.set(a, b + 1)
                        "F" -> {
                            fluid.set(a, b + 1)
                            nonSolid.set(a, b + 1)
                        }
                        "L" -> {
                            climbable.set(a, b + 1)
                            nonSolid.set(a, b + 1)
                        }
                    }
                }
            }
            loaded = true
        } catch (e: Exception) {
            // Tanpa file: hanya air (id 0) yang dianggap tembus.
        }
    }

    fun isSolid(id: Int) = !nonSolid.get(id)
    fun isFluid(id: Int) = fluid.get(id)
    fun isClimbable(id: Int) = climbable.get(id)
}

/** Nama item untuk inventori. assets/itemdata.txt dibuat saat build: baris "<id> <nama>". */
object ItemData {
    private val names = HashMap<Int, String>()

    @Volatile
    var loaded = false
        private set

    fun load(context: Context) {
        if (loaded) return
        try {
            context.assets.open("itemdata.txt").bufferedReader().useLines { lines ->
                for (line in lines) {
                    val i = line.indexOf(' ')
                    if (i <= 0) continue
                    val id = line.substring(0, i).toIntOrNull() ?: continue
                    names[id] = pretty(line.substring(i + 1).trim())
                }
            }
            loaded = true
        } catch (e: Exception) {
            // Tanpa file: nama item tampil sebagai "Item #id".
        }
    }

    fun nameOf(id: Int): String = names[id] ?: "Item #$id"

    private fun pretty(raw: String): String =
        raw.split('_').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
}

/** Penyimpan chunk: tiap section = 4096 block state id, null = semua udara. */
class World {
    class Chunk(val sections: Array<CharArray?>)

    private val chunks = ConcurrentHashMap<Long, Chunk>()

    @Volatile var minY = -64
    @Volatile var height = 384

    private fun key(cx: Int, cz: Int) = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)

    fun clear() = chunks.clear()
    fun chunkCount() = chunks.size
    fun putChunk(cx: Int, cz: Int, c: Chunk) { chunks[key(cx, cz)] = c }
    fun hasChunk(cx: Int, cz: Int) = chunks.containsKey(key(cx, cz))

    /** Mengembalikan -1 kalau chunk belum dimuat. */
    fun getBlock(x: Int, y: Int, z: Int): Int {
        val c = chunks[key(x shr 4, z shr 4)] ?: return -1
        val sy = (y - minY) shr 4
        if (sy < 0 || sy >= c.sections.size) return 0
        val sec = c.sections[sy] ?: return 0
        return sec[(((y - minY) and 15) shl 8) or ((z and 15) shl 4) or (x and 15)].code
    }

    fun setBlock(x: Int, y: Int, z: Int, id: Int) {
        val c = chunks[key(x shr 4, z shr 4)] ?: return
        val sy = (y - minY) shr 4
        if (sy < 0 || sy >= c.sections.size) return
        val sec = c.sections[sy] ?: CharArray(4096).also { c.sections[sy] = it }
        sec[(((y - minY) and 15) shl 8) or ((z and 15) shl 4) or (x and 15)] = id.toChar()
    }

    /** Chunk belum dimuat dianggap padat, supaya pemain tidak jatuh ke void. */
    fun isSolid(x: Int, y: Int, z: Int): Boolean {
        val id = getBlock(x, y, z)
        return id < 0 || BlockData.isSolid(id)
    }

    fun isFluid(x: Int, y: Int, z: Int): Boolean {
        val id = getBlock(x, y, z)
        return id >= 0 && BlockData.isFluid(id)
    }

    fun isClimbable(x: Int, y: Int, z: Int): Boolean {
        val id = getBlock(x, y, z)
        return id >= 0 && BlockData.isClimbable(id)
    }

    /** Buang chunk yang jauh dari pemain supaya memori tidak membengkak. */
    fun prune(cx: Int, cz: Int, radius: Int) {
        chunks.keys.removeIf { k ->
            val kx = (k shr 32).toInt()
            val kz = k.toInt()
            abs(kx - cx) > radius || abs(kz - cz) > radius
        }
    }

    fun blockPos(v: Double) = floor(v).toInt()
}

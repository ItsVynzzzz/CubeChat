package com.example.cubechat.world

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class PlayerState {
    var x = 0.0; var y = 0.0; var z = 0.0
    var vx = 0.0; var vy = 0.0; var vz = 0.0
    var yaw = 0f; var pitch = 0f
    var onGround = false
    var horizontalCollision = false
}

/**
 * Fisika pemain ala vanilla, 20 tick/detik, tanpa input (pemain diam).
 * Kotak tabrakan 0,6 x 1,8. Blok dianggap kubus penuh (slab/tangga belum akurat).
 */
class Physics(private val world: World) {
    companion object {
        const val HALF = 0.3
        const val HEIGHT = 1.8
        const val EPS = 1e-7
        const val GRAVITY = 0.08
    }

    /** Satu tick. Mengembalikan false kalau chunk di bawah pemain belum dimuat. */
    fun tick(s: PlayerState): Boolean {
        val bx = floor(s.x).toInt()
        val bz = floor(s.z).toInt()
        if (!world.hasChunk(bx shr 4, bz shr 4)) return false

        // Posisi dari server berada di dalam blok yang kita anggap padat
        // (mis. berdiri di slab): jangan tenggelam, diam saja.
        if (insideSolid(s)) {
            s.vx = 0.0; s.vy = 0.0; s.vz = 0.0
            s.onGround = true
            s.horizontalCollision = false
            return true
        }

        if (abs(s.vx) < 0.003) s.vx = 0.0
        if (abs(s.vy) < 0.003) s.vy = 0.0
        if (abs(s.vz) < 0.003) s.vz = 0.0

        val inFluid = world.isFluid(bx, floor(s.y + 0.001).toInt(), bz)

        move(s, s.vx, s.vy, s.vz)

        if (inFluid) {
            s.vx *= 0.8
            s.vy = s.vy * 0.8 - 0.02
            s.vz *= 0.8
        } else {
            val friction = if (s.onGround) 0.6 * 0.91 else 0.91
            s.vy = (s.vy - GRAVITY) * 0.98
            s.vx *= friction
            s.vz *= friction
        }
        return true
    }

    private fun insideSolid(s: PlayerState): Boolean {
        val y = floor(s.y + 0.01).toInt()
        val x0 = floor(s.x - HALF + EPS).toInt()
        val x1 = floor(s.x + HALF - EPS).toInt()
        val z0 = floor(s.z - HALF + EPS).toInt()
        val z1 = floor(s.z + HALF - EPS).toInt()
        for (x in x0..x1) for (z in z0..z1) if (world.isSolid(x, y, z)) return true
        return false
    }

    private fun move(s: PlayerState, dx: Double, dy: Double, dz: Double) {
        val cy = clampY(s, dy)
        s.y += cy
        val hitY = cy != dy

        val cx: Double
        val cz: Double
        if (abs(dx) >= abs(dz)) {
            cx = clampH(s, dx, true); s.x += cx
            cz = clampH(s, dz, false); s.z += cz
        } else {
            cz = clampH(s, dz, false); s.z += cz
            cx = clampH(s, dx, true); s.x += cx
        }

        s.horizontalCollision = cx != dx || cz != dz
        s.onGround = dy < 0 && hitY
        if (hitY) s.vy = 0.0
        if (cx != dx) s.vx = 0.0
        if (cz != dz) s.vz = 0.0
    }

    private fun clampY(s: PlayerState, dy: Double): Double {
        if (dy == 0.0) return 0.0
        val x0 = floor(s.x - HALF + EPS).toInt()
        val x1 = floor(s.x + HALF - EPS).toInt()
        val z0 = floor(s.z - HALF + EPS).toInt()
        val z1 = floor(s.z + HALF - EPS).toInt()
        var result = dy
        if (dy < 0) {
            for (by in floor(s.y + EPS).toInt() downTo floor(s.y + dy).toInt())
                for (bx in x0..x1) for (bz in z0..z1)
                    if (world.isSolid(bx, by, bz)) {
                        val top = by + 1.0
                        if (top <= s.y + EPS) result = max(result, min(0.0, top - s.y))
                    }
        } else {
            val head = s.y + HEIGHT
            for (by in floor(head - EPS).toInt()..floor(head + dy).toInt())
                for (bx in x0..x1) for (bz in z0..z1)
                    if (world.isSolid(bx, by, bz) && by >= head - EPS)
                        result = min(result, max(0.0, by - head))
        }
        return result
    }

    private fun clampH(s: PlayerState, d: Double, xAxis: Boolean): Double {
        if (d == 0.0) return 0.0
        val pos = if (xAxis) s.x else s.z
        val other = if (xAxis) s.z else s.x
        val o0 = floor(other - HALF + EPS).toInt()
        val o1 = floor(other + HALF - EPS).toInt()
        val y0 = floor(s.y + EPS).toInt()
        val y1 = floor(s.y + HEIGHT - EPS).toInt()
        var result = d
        if (d > 0) {
            val edge = pos + HALF
            for (b in floor(edge - EPS).toInt()..floor(edge + d).toInt())
                for (o in o0..o1) for (yy in y0..y1) {
                    val solid = if (xAxis) world.isSolid(b, yy, o) else world.isSolid(o, yy, b)
                    if (solid && b >= edge - EPS) result = min(result, max(0.0, b - edge))
                }
        } else {
            val edge = pos - HALF
            for (b in floor(edge + EPS).toInt() downTo floor(edge + d).toInt())
                for (o in o0..o1) for (yy in y0..y1) {
                    val solid = if (xAxis) world.isSolid(b, yy, o) else world.isSolid(o, yy, b)
                    if (solid && b + 1 <= edge + EPS) result = max(result, min(0.0, (b + 1) - edge))
                }
        }
        return result
    }
}

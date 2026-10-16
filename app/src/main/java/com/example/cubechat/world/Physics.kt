package com.example.cubechat.world

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class PlayerState {
    var x = 0.0; var y = 0.0; var z = 0.0
    var vx = 0.0; var vy = 0.0; var vz = 0.0
    var yaw = 0f; var pitch = 0f
    var onGround = false
    var horizontalCollision = false
    var sprinting = false
    var sneaking = false
    var flying = false
    var jumpDelay = 0
}

/** Tombol yang sedang ditekan pemain (diisi dari UI). */
class InputState {
    var forward = false
    var back = false
    var left = false
    var right = false
    var jump = false
    var sneak = false
    var sprint = false
}

/** Kemampuan pemain dari server (paket abilities) dan mode permainan. */
class Abilities {
    var canFly = false
    var walkSpeed = 0.1f
    var flySpeed = 0.05f
    var noClip = false // mode spectator
}

/**
 * Fisika pemain ala vanilla, 20 tick/detik. Kotak tabrakan 0,6 x 1,8.
 * Mendukung: jalan, lari, lompat (termasuk lompat-lari), jongkok (tidak jatuh dari tepi),
 * renang, memanjat tangga/sulur, terbang (creative) dan spectator.
 * Blok dianggap kubus penuh (slab/tangga/pagar belum akurat).
 */
class Physics(private val world: World) {
    companion object {
        const val HALF = 0.3
        const val HEIGHT = 1.8
        const val EPS = 1e-7
        const val GRAVITY = 0.08
    }

    /** Satu tick. Mengembalikan false kalau chunk di bawah pemain belum dimuat. */
    fun tick(s: PlayerState, inp: InputState, ab: Abilities): Boolean {
        val bx = floor(s.x).toInt()
        val bz = floor(s.z).toInt()
        if (!world.hasChunk(bx shr 4, bz shr 4)) return false

        if (abs(s.vx) < 0.003) s.vx = 0.0
        if (abs(s.vy) < 0.003) s.vy = 0.0
        if (abs(s.vz) < 0.003) s.vz = 0.0

        // --- input -> impuls gerak
        var fwd = (if (inp.forward) 1f else 0f) - (if (inp.back) 1f else 0f)
        var str = (if (inp.left) 1f else 0f) - (if (inp.right) 1f else 0f)
        val sneakNow = inp.sneak && !s.flying && !ab.noClip
        s.sneaking = sneakNow
        fwd *= 0.98f
        str *= 0.98f
        if (sneakNow) { fwd *= 0.3f; str *= 0.3f }
        s.sprinting = inp.sprint && fwd > 0f && !sneakNow && !s.horizontalCollision

        val footY = floor(s.y + 0.001).toInt()
        val inFluid = !s.flying && !ab.noClip && world.isFluid(bx, footY, bz)
        val climbing = !s.flying && !ab.noClip && !inFluid && world.isClimbable(bx, footY, bz)
        val wasOnGround = s.onGround

        // Posisi dari server berada di dalam blok yang kita anggap padat (mis. berdiri di slab):
        // jalan tanpa tabrakan sampai keluar, jangan membeku di tempat.
        if (!ab.noClip && insideSolid(s)) {
            val accel = if (s.sprinting) ab.walkSpeed * 1.3 else ab.walkSpeed.toDouble()
            moveRelative(s, str, fwd, accel)
            s.x += s.vx; s.z += s.vz
            s.vx *= 0.546; s.vz *= 0.546
            s.vy = 0.0
            s.onGround = true
            s.horizontalCollision = false
            return true
        }

        // --- lompat
        if (s.jumpDelay > 0) s.jumpDelay--
        if (inp.jump) {
            if (!s.flying && !ab.noClip && !inFluid && !climbing && s.onGround && s.jumpDelay == 0) {
                s.vy = 0.42
                if (s.sprinting) {
                    val r = s.yaw * PI / 180.0
                    s.vx -= sin(r) * 0.2
                    s.vz += cos(r) * 0.2
                }
                s.jumpDelay = 10
            }
        } else {
            s.jumpDelay = 0
        }

        when {
            ab.noClip || s.flying -> flyTick(s, inp, ab, str, fwd, wasOnGround)
            inFluid -> swimTick(s, inp, str, fwd)
            else -> walkTick(s, inp, ab, str, fwd, climbing, wasOnGround)
        }
        return true
    }

    private fun walkTick(
        s: PlayerState, inp: InputState, ab: Abilities,
        str: Float, fwd: Float, climbing: Boolean, wasOnGround: Boolean
    ) {
        // Di tanah: kecepatan jalan (0,1; lari x1,3). Di udara: 0,02 (lari x1,3).
        val accel = if (wasOnGround) {
            ab.walkSpeed * (if (s.sprinting) 1.3 else 1.0)
        } else {
            0.02 * (if (s.sprinting) 1.3 else 1.0)
        }
        moveRelative(s, str, fwd, accel)

        if (climbing) {
            s.vx = s.vx.coerceIn(-0.15, 0.15)
            s.vz = s.vz.coerceIn(-0.15, 0.15)
            s.vy = max(s.vy, -0.15)
            if (s.sneaking && s.vy < 0.0) s.vy = 0.0
        }

        var dx = s.vx
        var dz = s.vz
        if (s.sneaking && wasOnGround && s.vy <= 0.0) {
            val r = backOffFromEdge(s, dx, dz)
            dx = r[0]; dz = r[1]
        }
        move(s, dx, s.vy, dz, true)

        if (climbing && (s.horizontalCollision || inp.jump)) s.vy = 0.2

        val friction = if (wasOnGround) 0.6 * 0.91 else 0.91
        s.vy = (s.vy - GRAVITY) * 0.98
        s.vx *= friction
        s.vz *= friction
    }

    private fun swimTick(s: PlayerState, inp: InputState, str: Float, fwd: Float) {
        moveRelative(s, str, fwd, 0.02)
        if (inp.jump) s.vy += 0.04
        if (inp.sneak) s.vy -= 0.04
        move(s, s.vx, s.vy, s.vz, true)

        val slow = if (s.sprinting) 0.9 else 0.8
        s.vx *= slow
        s.vz *= slow
        s.vy = s.vy * 0.8 - GRAVITY / 16.0
        // Keluar dari air ke tepi kolam.
        if (s.horizontalCollision && !boxHitsSolid(s.x + s.vx, s.y + 0.6, s.z + s.vz)) s.vy = 0.3
    }

    private fun flyTick(
        s: PlayerState, inp: InputState, ab: Abilities,
        str: Float, fwd: Float, wasOnGround: Boolean
    ) {
        val speed = ab.flySpeed * (if (s.sprinting || inp.sprint) 2.0 else 1.0)
        moveRelative(s, str, fwd, speed)
        if (inp.jump) s.vy += ab.flySpeed * 3.0
        if (inp.sneak) s.vy -= ab.flySpeed * 3.0

        if (ab.noClip) {
            s.x += s.vx; s.y += s.vy; s.z += s.vz
            s.onGround = false
            s.horizontalCollision = false
        } else {
            move(s, s.vx, s.vy, s.vz, true)
        }
        val friction = if (wasOnGround) 0.6 * 0.91 else 0.91
        s.vx *= friction
        s.vz *= friction
        s.vy *= 0.6
    }

    /** Tambah percepatan sesuai arah hadap (yaw). str: kiri positif, fwd: maju positif. */
    private fun moveRelative(s: PlayerState, str: Float, fwd: Float, speed: Double) {
        val d = (str * str + fwd * fwd).toDouble()
        if (d < 1.0E-7) return
        var sx = str.toDouble()
        var sz = fwd.toDouble()
        if (d > 1.0) {
            val len = sqrt(d)
            sx /= len; sz /= len
        }
        sx *= speed; sz *= speed
        val r = s.yaw * PI / 180.0
        val sn = sin(r)
        val cs = cos(r)
        s.vx += sx * cs - sz * sn
        s.vz += sz * cs + sx * sn
    }

    /** Jongkok di tepi: kurangi gerak supaya tidak jatuh (seperti vanilla). */
    private fun backOffFromEdge(s: PlayerState, dx0: Double, dz0: Double): DoubleArray {
        var dx = dx0
        var dz = dz0
        while (dx != 0.0 && canFall(s, dx, 0.0)) {
            dx = if (dx < 0.05 && dx >= -0.05) 0.0 else if (dx > 0.0) dx - 0.05 else dx + 0.05
        }
        while (dz != 0.0 && canFall(s, 0.0, dz)) {
            dz = if (dz < 0.05 && dz >= -0.05) 0.0 else if (dz > 0.0) dz - 0.05 else dz + 0.05
        }
        while (dx != 0.0 && dz != 0.0 && canFall(s, dx, dz)) {
            dx = if (dx < 0.05 && dx >= -0.05) 0.0 else if (dx > 0.0) dx - 0.05 else dx + 0.05
            dz = if (dz < 0.05 && dz >= -0.05) 0.0 else if (dz > 0.0) dz - 0.05 else dz + 0.05
        }
        return doubleArrayOf(dx, dz)
    }

    private fun canFall(s: PlayerState, dx: Double, dz: Double) =
        !boxHitsSolid(s.x + dx, s.y - 0.6, s.z + dz)

    private fun boxHitsSolid(x: Double, y: Double, z: Double): Boolean {
        val x0 = floor(x - HALF + EPS).toInt()
        val x1 = floor(x + HALF - EPS).toInt()
        val z0 = floor(z - HALF + EPS).toInt()
        val z1 = floor(z + HALF - EPS).toInt()
        val y0 = floor(y + EPS).toInt()
        val y1 = floor(y + HEIGHT - EPS).toInt()
        for (bx in x0..x1) for (bz in z0..z1) for (by in y0..y1)
            if (world.isSolid(bx, by, bz)) return true
        return false
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

    @Suppress("SameParameterValue")
    private fun move(s: PlayerState, dx: Double, dy: Double, dz: Double, collide: Boolean) {
        if (!collide) {
            s.x += dx; s.y += dy; s.z += dz
            return
        }
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

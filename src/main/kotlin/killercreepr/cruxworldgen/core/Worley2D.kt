package killercreepr.cruxworldgen.core

import kotlin.math.floor
import kotlin.math.sqrt

class Worley2D(
    private val seed: Long,
    private val cellSize: Double = 32.0
) {

    fun noiseF1(x: Double, z: Double): Double {
        return compute(x, z).first
    }

    fun noiseF2MinusF1(x: Double, z: Double): Double {
        val (f1, f2) = compute(x, z)
        return f2 - f1
    }

    private fun compute(xInput: Double, zInput: Double): Pair<Double, Double> {
        val x = xInput / cellSize
        val z = zInput / cellSize

        val cellX = fastFloor(x)
        val cellZ = fastFloor(z)

        var f1 = Double.MAX_VALUE
        var f2 = Double.MAX_VALUE

        for (dx in -1..1) {
            for (dz in -1..1) {

                val cx = cellX + dx
                val cz = cellZ + dz

                val fx = cx + randomDouble(cx, cz, 0)
                val fz = cz + randomDouble(cx, cz, 1)

                val dist = distanceSquared(x, z, fx, fz)

                if (dist < f1) {
                    f2 = f1
                    f1 = dist
                } else if (dist < f2) {
                    f2 = dist
                }
            }
        }

        return sqrt(f1) to sqrt(f2)
    }

    private fun randomDouble(x: Int, z: Int, salt: Int): Double {
        var n = x * 374761393L + z * 668265263L + seed + salt * 1442695040888963407L
        n = (n xor (n shr 13)) * 1274126177L
        n = n xor (n shr 16)
        return (n and 0x7FFFFFFF).toDouble() / Int.MAX_VALUE.toDouble()
    }

    private fun fastFloor(d: Double): Int {
        return floor(d).toInt()
    }

    private fun distanceSquared(x1: Double, z1: Double, x2: Double, z2: Double): Double {
        val dx = x1 - x2
        val dz = z1 - z2
        return dx * dx + dz * dz
    }
}

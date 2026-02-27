package killercreepr.cruxworldgen.api.noise

import kotlin.math.floor
import kotlin.math.sqrt

data class Worley3DResult(
  val f1: Double,
  val f2: Double,
  val cellX1: Int,
  val cellY1: Int,
  val cellZ1: Int,
  val cellX2: Int,
  val cellY2: Int,
  val cellZ2: Int
)

class Worley3D(
  private val seed: Long,
  private val cellSize: Double = 32.0
) {

    fun sample(xInput: Double, yInput: Double, zInput: Double): Worley3DResult {
        val x = xInput / cellSize
        val y = yInput / cellSize
        val z = zInput / cellSize

        val cellX = fastFloor(x)
        val cellY = fastFloor(y)
        val cellZ = fastFloor(z)

        var f1 = Double.MAX_VALUE
        var f2 = Double.MAX_VALUE

        var nearestX = 0
        var nearestY = 0
        var nearestZ = 0
        var secondX = 0
        var secondY = 0
        var secondZ = 0

        for (dx in -1..1) {
            for (dy in -1..1) {
                for (dz in -1..1) {

                    val cx = cellX + dx
                    val cy = cellY + dy
                    val cz = cellZ + dz

                    val fx = cx + randomDouble(cx, cy, cz, 0)
                    val fy = cy + randomDouble(cx, cy, cz, 1)
                    val fz = cz + randomDouble(cx, cy, cz, 2)

                    val dist = distanceSquared(x, y, z, fx, fy, fz)

                    if (dist < f1) {
                        // shift previous nearest to second
                        f2 = f1
                        secondX = nearestX
                        secondY = nearestY
                        secondZ = nearestZ

                        f1 = dist
                        nearestX = cx
                        nearestY = cy
                        nearestZ = cz
                    } else if (dist < f2) {
                        f2 = dist
                        secondX = cx
                        secondY = cy
                        secondZ = cz
                    }
                }
            }
        }

        return Worley3DResult(
          f1 = sqrt(f1),
          f2 = sqrt(f2),
          cellX1 = nearestX,
          cellY1 = nearestY,
          cellZ1 = nearestZ,
          cellX2 = secondX,
          cellY2 = secondY,
          cellZ2 = secondZ
        )
    }

    private fun randomDouble(x: Int, y: Int, z: Int, salt: Int): Double {
        var n = x * 374761393L +
          y * 668265263L +
          z * 144664877L +
          seed +
          salt * 1442695040888963407L

        n = (n xor (n shr 13)) * 1274126177L
        n = n xor (n shr 16)

        return (n and 0x7FFFFFFF).toDouble() / Int.MAX_VALUE.toDouble()
    }

    private fun fastFloor(d: Double): Int = floor(d).toInt()

    private fun distanceSquared(
      x1: Double, y1: Double, z1: Double,
      x2: Double, y2: Double, z2: Double
    ): Double {
        val dx = x1 - x2
        val dy = y1 - y2
        val dz = z1 - z2
        return dx * dx + dy * dy + dz * dz
    }
}

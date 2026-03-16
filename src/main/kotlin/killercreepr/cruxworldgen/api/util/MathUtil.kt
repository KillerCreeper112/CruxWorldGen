package killercreepr.cruxworldgen.api.util

object MathUtil {
  fun blockIndex(localX: Int, localZ: Int, blockY: Int, minBlockY: Int, chunkWidth: Int, chunkDepth: Int): Int {
    val localY = blockY - minBlockY
    return (localY * chunkDepth + localZ) * chunkWidth + localX
  }

  fun localXFromWorld(worldX: Int, chunkWidth: Int): Int =
    Math.floorMod(worldX, chunkWidth)

  fun localZFromWorld(worldZ: Int, chunkDepth: Int): Int =
    Math.floorMod(worldZ, chunkDepth)

  fun columnIndex(localX: Int, localZ: Int, chunkWidth: Int): Int = localZ * chunkWidth + localX

  fun cellIndex(cellX: Int, cellZ: Int, cellY: Int, biomeCellCountX: Int, biomeCellCountZ: Int): Int {
    return (cellY * biomeCellCountZ + cellZ) * biomeCellCountX + cellX
  }

  fun cornerColumnIndex(cornerX: Int, cornerZ: Int, cellCountX: Int): Int {
    return cornerZ * (cellCountX + 1) + cornerX
  }

  fun cornerIndex(cx: Int, cz: Int, cy: Int, cellsX: Int, cellsZ: Int): Int =
    (cy * (cellsZ + 1) + cz) * (cellsX + 1) + cx
  fun cellYFromWorld(worldY : Int, cellSize : Int, minY : Int) = Math.floorDiv(worldY - minY, cellSize)

  fun pseudoRandomInfluence(x: Int, y: Int, z: Int, seed: Int = 0): Double {
    var h = x.toLong() * 374761393L + y.toLong() * 668265263L + z.toLong() * 0x27d4eb2dL + seed.toLong()
    h = (h xor (h shr 13)) * 1274126177L
    val unsigned = h xor (h shr 16)
    return (unsigned and 0xFFFFFFFFL).toDouble() / 0xFFFFFFFFL
  }

  /*fun rotateXZ(x: Double, z: Double, angleDegrees: Double): Pair<Double, Double> {
    val radians = angleDegrees * PI / 180.0
    val c = cos(radians)
    val s = sin(radians)

    val rx = x * c + z * s
    val rz = -x * s + z * c
    return rx to rz
  }*/
}
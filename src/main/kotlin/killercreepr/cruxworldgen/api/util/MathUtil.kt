package killercreepr.cruxworldgen.api.util

object MathUtil {
  fun blockIndex(localX: Int, localZ: Int, blockY: Int, minBlockY: Int, chunkWidth: Int, chunkDepth: Int): Int {
    val localY = blockY - minBlockY
    return (localY * chunkDepth + localZ) * chunkWidth + localX
  }

  fun cornerIndex(cx: Int, cz: Int, cy: Int, cellsX: Int, cellsZ: Int): Int =
    (cy * (cellsZ + 1) + cz) * (cellsX + 1) + cx

  fun columnIndex(localX: Int, localZ: Int, chunkWidth: Int): Int = localZ * chunkWidth + localX

  fun cellIndex(cellX: Int, cellZ: Int, cellY: Int, biomeCellCountX: Int, biomeCellCountZ: Int): Int {
    return (cellY * biomeCellCountZ + cellZ) * biomeCellCountX + cellX
  }
  fun cellYFromWorld(worldY : Int, cellSize : Int, minY : Int) = Math.floorDiv(worldY - minY, cellSize)
}
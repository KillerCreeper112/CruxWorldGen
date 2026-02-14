package killercreepr.cruxworldgen.core.decor

import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.decor.PropPoint
import killercreepr.cruxworldgen.api.prop.PropPointGrid
import killercreepr.cruxworldgen.api.util.HashUtil.hash2D

class SimplePropPointGrid(
  private val spacingBlocks: Int = 8,  // 6..12 typical
  private val jitterBlocks: Int = 3
) : PropPointGrid {
  override fun pointsForChunk(ctx: GenerateContext, chunkX: Int, chunkZ: Int): List<PropPoint> {
    val chunkWorldX = chunkX * 16
    val chunkWorldZ = chunkZ * 16

    val points = ArrayList<PropPoint>()
    val baseSeed = ctx.worldContext.seed

    val startX = chunkWorldX - spacingBlocks
    val startZ = chunkWorldZ - spacingBlocks
    val endX = chunkWorldX + 16 + spacingBlocks
    val endZ = chunkWorldZ + 16 + spacingBlocks

    var gridX = Math.floorDiv(startX, spacingBlocks) * spacingBlocks
    while (gridX <= endX) {

      var gridZ = Math.floorDiv(startZ, spacingBlocks) * spacingBlocks
      while (gridZ <= endZ) {

        val pointSeed = hash2D(baseSeed, gridX, gridZ)
        val jitterX = ((pointSeed ushr 0).toInt() and 0x7FFFFFFF) % (jitterBlocks * 2 + 1) - jitterBlocks
        val jitterZ = ((pointSeed ushr 21).toInt() and 0x7FFFFFFF) % (jitterBlocks * 2 + 1) - jitterBlocks

        val worldX = gridX + jitterX
        val worldZ = gridZ + jitterZ

        val localX = worldX - chunkWorldX
        val localZ = worldZ - chunkWorldZ

        if (localX in 0..15 && localZ in 0..15) {
          points.add(SimplePropPoint(worldX, worldZ, localX, localZ, pointSeed))
        }

        gridZ += spacingBlocks
      }

      gridX += spacingBlocks
    }

    return points
  }
}
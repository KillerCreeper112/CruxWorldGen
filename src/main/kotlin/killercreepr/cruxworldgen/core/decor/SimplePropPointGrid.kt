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
  //todo
  /*private fun runVolumetricDecorationsForPass(
    region: LimitedRegion,
    chunkX: Int,
    chunkZ: Int,
    points: List<PropPoint>,
    pass: DecorationPass,
    sampleBlendAt: (Int, Int) -> BiomeBlendSample,
    sampleVolumetricBiomeAt: (Int, Int, Int) -> VolumetricBiome?,
    sampleSurfaceYAt: (Int, Int) -> Int
  ) {
    val minY = region.ctx.chunkContext.minHeight
    val maxY = region.ctx.chunkContext.maxHeight - 1

    // Match your volumetric cache resolution (4x4x4 etc.)
    val yStep = 4 // or inject / use volumetricBiomeCellSize

    for (point in points) {
      val worldX = point.worldX
      val worldZ = point.worldZ
      val surfaceBlend = sampleBlendAt(worldX, worldZ)
      val surfaceY = sampleSurfaceYAt(worldX, worldZ)

      var y = maxY
      while (y >= minY) {
        val biome3D = sampleVolumetricBiomeAt(worldX, y, worldZ)

        // Pull decorations from the 3D biome, but only volumetric ones
        val decorations = biome3D.decorations

        for (decoration in decorations) {
          if (decoration.pass != pass) continue

          // If your decoration API only understands PropPoint (x,z), you can extend it
          // or create a VolumetricPropPoint / placement context.
          val volPoint = SimpleVolumetricPropPoint(
            worldX = worldX,
            worldY = y,
            worldZ = worldZ,
            localX = point.localX,
            localY = y - minY,
            localZ = point.localZ,
            seed = mixSeed(point.seed, y),
            surfaceY = surfaceY
          )

          if (decoration is VolumetricDecoration) {
            if (!decoration.shouldTry3D(region, volPoint, surfaceBlend, biome3D)) continue
            val placement = decoration.findPlacement3D(region, volPoint, surfaceBlend, biome3D) ?: continue

            // Safety gate: ensure final placement still lands in same 3D biome
            val finalBiome = sampleVolumetricBiomeAt(placement.worldX, placement.worldY, placement.worldZ)
            if (finalBiome !== biome3D) continue

            decoration.place3D(region, placement, surfaceBlend, biome3D)
          }
        }

        y -= yStep
      }
    }
  }*/

  //private fun mixSeed(seed: Long, y: Int): Long = seed xor (y.toLong() * 0x9E3779B97F4A7C15L)
}
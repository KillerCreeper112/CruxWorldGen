package killercreepr.cruxworldgen.core.decor

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.decor.DecorationPass
import killercreepr.cruxworldgen.api.decor.DecorationPipeline
import killercreepr.cruxworldgen.api.decor.PropPoint
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.prop.PropPointGrid

class SimpleDecorationPipeline(override val grid: PropPointGrid) : DecorationPipeline {
  override fun runAllPasses(
    region: LimitedRegion,
    chunkX: Int,
    chunkZ: Int,
    sampleBlendAt: (worldX: Int, worldZ: Int) -> BiomeBlendSample,
    sampleBiomeAt: (Int, Int, Int) -> Biome,
    sampleSurfaceYAt: (Int, Int) -> Int
  ){
    val ctx = region.ctx
    val points = grid.pointsForChunk(ctx, chunkX, chunkZ)

    for (point in points) {
      val blend = sampleBlendAt(point.worldX, point.worldZ)

      // Collect decorations from the blend

      for (pass in DecorationPass.entries) {
        val decorations = blend.primaryBiome().decorations

        for (decoration in decorations) {
          if(decoration.pass != pass) continue
          if (!decoration.shouldTry(region, point, blend)) continue
          val placement = decoration.findPlacement(region, point, blend) ?: continue
          decoration.place(region, placement, blend)
        }

        runVolumetricDecorationsPass(
          region, chunkX, chunkZ, points, pass, sampleBlendAt, sampleBiomeAt, sampleSurfaceYAt
        )
      }
    }
  }

  override fun runVolumetricDecorationsPass(
    region: LimitedRegion,
    chunkX: Int,
    chunkZ: Int,
    points: List<PropPoint>,
    pass: DecorationPass,
    sampleBlendAt: (Int, Int) -> BiomeBlendSample,
    sampleBiomeAt: (Int, Int, Int) -> Biome,
    sampleSurfaceYAt: (Int, Int) -> Int
  ) {
    val minY = region.ctx.chunkContext.minHeight
    val maxY = region.ctx.chunkContext.maxHeight - 1

    val yStep = 4

    for (point in points) {
      val worldX = point.worldX
      val worldZ = point.worldZ
      val surfaceBlend = sampleBlendAt(worldX, worldZ)
      //val surfaceY = sampleSurfaceYAt(worldX, worldZ)

      var y = maxY
      while (y >= minY) {
        val biome3D = sampleBiomeAt(worldX, y, worldZ)

        val decorations = biome3D.volumetricDecorations

        for (decoration in decorations) {
          if (decoration.pass != pass) continue

          val volPoint = SimpleVolumetricPropPoint(
            worldX = worldX,
            worldZ = worldZ,
            worldY = y,
            localX = point.localX,
            localZ = point.localZ,
            seed = mixSeed(point.seed, y)
          )

          if (!decoration.shouldTry(region, volPoint, surfaceBlend, biome3D)) continue
          val placement = decoration.findPlacement(region, volPoint, surfaceBlend, biome3D) ?: continue
          decoration.place(region, placement, surfaceBlend, biome3D)
        }

        y -= yStep
      }
    }
  }

  fun mixSeed(seed: Long, y: Int): Long = seed xor (y.toLong() * 0x9E3779B97F4A715L)
}
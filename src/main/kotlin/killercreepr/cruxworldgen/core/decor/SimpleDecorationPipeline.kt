package killercreepr.cruxworldgen.core.decor

import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.decor.DecorationPass
import killercreepr.cruxworldgen.api.decor.DecorationPipeline
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.prop.PropPointGrid

class SimpleDecorationPipeline(override val grid: PropPointGrid) : DecorationPipeline {
  override fun runAllPasses(
    region: LimitedRegion,
    chunkX: Int,
    chunkZ: Int,
    sampleBlendAt: (worldX: Int, worldZ: Int) -> BiomeBlendSample
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
      }
    }
  }
}
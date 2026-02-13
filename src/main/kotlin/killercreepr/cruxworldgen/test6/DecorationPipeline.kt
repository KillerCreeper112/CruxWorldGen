package killercreepr.cruxworldgen.test6

import killercreepr.cruxworldgen.test6.biome.BiomeBlendSample
import killercreepr.cruxworldgen.test6.context.GenerateContext
import killercreepr.cruxworldgen.test6.decor.Decoration
import killercreepr.cruxworldgen.test6.decor.DecorationPass
import killercreepr.cruxworldgen.test6.prop.PropPointGrid
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags.decorations

class DecorationPipeline(
  private val grid: PropPointGrid
) {

  fun runAllPasses(
    ctx: GenerateContext,
    chunkX: Int,
    chunkZ: Int,
    sampleBlendAt: (worldX: Int, worldZ: Int) -> BiomeBlendSample
  ){
    val points = grid.pointsForChunk(ctx, chunkX, chunkZ)

    for (point in points) {
      val blend = sampleBlendAt(point.worldX, point.worldZ)

      // Collect decorations from the blend

      for (pass in DecorationPass.entries) {
        val decorations = blend.primaryBiome().decorations

        for (decoration in decorations) {
          if(decoration.pass != pass) continue
          if (!decoration.shouldTry(ctx, point, blend)) continue
          val placement = decoration.findPlacement(ctx, point, blend) ?: continue
          decoration.place(ctx, placement, blend)
        }
      }
    }
  }
}

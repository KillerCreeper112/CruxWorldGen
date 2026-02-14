package killercreepr.cruxworldgen.api.decor

import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.prop.PropPointGrid

interface DecorationPipeline{
  val grid: PropPointGrid

  fun runAllPasses(
    ctx: GenerateContext,
    chunkX: Int,
    chunkZ: Int,
    sampleBlendAt: (worldX: Int, worldZ: Int) -> BiomeBlendSample
  )
}

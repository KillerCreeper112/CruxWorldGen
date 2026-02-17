package killercreepr.cruxworldgen.core.underground

import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample

interface UndergroundFeaturePipeline {
  fun runForChunk(
    ctx: GenerateContext,
    chunkX: Int,
    chunkZ: Int,
    biomeBlendSampler: (Int, Int) -> BiomeBlendSample
  )
}

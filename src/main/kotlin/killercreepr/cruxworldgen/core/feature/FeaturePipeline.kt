package killercreepr.cruxworldgen.core.feature

import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample

interface FeaturePipeline {
  fun runForChunk(
    ctx: GenerateContext,
    chunkX: Int,
    chunkZ: Int,
    biomeBlendSampler: (Int, Int) -> BiomeBlendSample
  )
}

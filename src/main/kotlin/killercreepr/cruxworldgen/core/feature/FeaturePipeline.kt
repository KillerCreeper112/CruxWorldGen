package killercreepr.cruxworldgen.core.feature

import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample

interface FeaturePipeline {
  fun runForChunk(
    region: LimitedRegion,
    chunkX: Int,
    chunkZ: Int,
    biomeBlendSampler: (Int, Int) -> BiomeBlendSample
  )
}

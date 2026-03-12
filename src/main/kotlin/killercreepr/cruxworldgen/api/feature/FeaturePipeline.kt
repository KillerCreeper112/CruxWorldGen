package killercreepr.cruxworldgen.api.feature

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample

interface FeaturePipeline {
  fun runForChunk(
    region: LimitedRegion,
    chunkX: Int,
    chunkZ: Int,
    biomeBlendSampler: (Int, Int) -> BiomeBlendSample,
    volumetricBiomeSampler: ((Int, Int, Int) -> Biome)? = null
  )
}
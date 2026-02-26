package killercreepr.cruxworldgen.api.biome

import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample

interface BiomeRegistry {

  val biomes: List<Biome>
  val biomeCellSizeBlocks: Int
  // How wide the transition band is (smaller = sharper borders)
  val blendRadiusBlocks: Double

  fun sampleBiomeBlend(generateCtx: GenerateContext, worldX: Int, worldZ: Int): BiomeBlendSample
}
package killercreepr.cruxworldgen.api.generation

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.context.volumetric.VolBiomeBlendSample
import killercreepr.cruxworldgen.core.generation.SimpleBiomeBlendSample

interface BiomeBlendSample {
  companion object{
    fun biomeBlendSample(
      weightedBiomes: List<WeightedBiome>,
      edgeContext: BiomeEdgeContext
    ) = SimpleBiomeBlendSample(weightedBiomes, edgeContext)
  }

  val weightedBiomes: List<WeightedBiome>
  val edgeContext: BiomeEdgeContext

  fun primaryBiome(): Biome

  fun totalWeight(): Double
}
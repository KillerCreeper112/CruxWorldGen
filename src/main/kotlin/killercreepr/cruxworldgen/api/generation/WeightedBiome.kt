package killercreepr.cruxworldgen.api.generation

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.core.generation.SimpleWeightedBiome

interface WeightedBiome {
  companion object{
    fun weightedBiome(biome: Biome, weight : Double) : WeightedBiome = SimpleWeightedBiome(biome, weight)
  }

  val biome: Biome
  val weight: Double
}
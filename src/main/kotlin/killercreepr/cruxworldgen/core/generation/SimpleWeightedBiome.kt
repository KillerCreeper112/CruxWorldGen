package killercreepr.cruxworldgen.core.generation

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.generation.WeightedBiome

class SimpleWeightedBiome(
  override val biome: Biome,
  override val weight: Double
) : WeightedBiome {
}
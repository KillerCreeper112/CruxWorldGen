package killercreepr.cruxworldgen.core.generation

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.generation.WeightedBiome

class SimpleBiomeBlendSample(
  override val weightedBiomes: List<WeightedBiome>,
  override val edgeContext: BiomeEdgeContext
) : BiomeBlendSample {

  override fun primaryBiome(): Biome = weightedBiomes.maxBy { it.weight }.biome

  override fun totalWeight(): Double = weightedBiomes.sumOf { it.weight }
}
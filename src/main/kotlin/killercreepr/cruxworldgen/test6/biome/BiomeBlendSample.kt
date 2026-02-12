package killercreepr.cruxworldgen.test6.biome

data class WeightedBiome(
  val biome: Biome,
  val weight: Double
)

data class BiomeBlendSample(
  val weightedBiomes: List<WeightedBiome>,  // usually size 2..4
  val edgeContext: BiomeEdgeContext
) {
  fun primaryBiome(): Biome = weightedBiomes.maxBy { it.weight }.biome

  fun totalWeight(): Double = weightedBiomes.sumOf { it.weight }
}

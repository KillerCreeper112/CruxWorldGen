package killercreepr.cruxworldgen.api.context.volumetric

import killercreepr.cruxworldgen.api.biome.volumetric.VolumetricBiome
import killercreepr.cruxworldgen.api.biome.volumetric.WeightedVolBiome

data class VolBiomeBlendSample(
  val weighted: List<WeightedVolBiome>
) {
  fun isEmpty() = weighted.isEmpty()
  fun dominant(): VolumetricBiome = weighted.maxBy { it.weight }.biome
}
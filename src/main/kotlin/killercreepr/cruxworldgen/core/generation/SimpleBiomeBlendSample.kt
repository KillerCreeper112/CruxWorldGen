package killercreepr.cruxworldgen.core.generation

import io.papermc.paper.util.ItemComponentSanitizer.override
import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.generation.WeightedBiome

class SimpleBiomeBlendSample(
  override val weightedBiomes: List<WeightedBiome>,
  override val edgeContext: BiomeEdgeContext
) : BiomeBlendSample {

  var cachePrimaryBiome : Biome? = null

  override fun primaryBiome(): Biome = if(cachePrimaryBiome == null){
    cachePrimaryBiome = weightedBiomes.maxBy { it.weight }.biome
    cachePrimaryBiome!!
  }else cachePrimaryBiome!!

  override fun totalWeight(): Double = weightedBiomes.sumOf { it.weight }
}
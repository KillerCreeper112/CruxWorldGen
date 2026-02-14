package killercreepr.cruxworldgen.core.biome

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.biome.BiomeRegistry
import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.generation.WeightedBiome
import killercreepr.cruxworldgen.api.util.HashUtil.HASH_SALT
import killercreepr.cruxworldgen.api.util.HashUtil.hash2D

class SimpleBiomeRegistry(
  override val biomes: List<Biome>,
  override val biomeCellSizeBlocks: Int,
  override val blendRadiusBlocks: Double
) : BiomeRegistry {
  override fun sampleBiomeBlend(generateCtx: GenerateContext, worldX: Int, worldZ: Int): BiomeBlendSample {
    val cellX = kotlin.math.floor(worldX.toDouble() / biomeCellSizeBlocks.toDouble()).toInt()
    val cellZ = kotlin.math.floor(worldZ.toDouble() / biomeCellSizeBlocks.toDouble()).toInt()

    data class CandidateBiome(
      val biome: Biome,
      val distance: Double
    )

    val candidates = ArrayList<CandidateBiome>(25)

    // 5x5 search is much more stable with jittered points
    for (neighborCellX in (cellX - 2)..(cellX + 2)) {
      for (neighborCellZ in (cellZ - 2)..(cellZ + 2)) {

        val center = cellPoint(generateCtx.worldContext.seed, neighborCellX, neighborCellZ, biomeCellSizeBlocks)

        val dx = (worldX - center.worldX).toDouble()
        val dz = (worldZ - center.worldZ).toDouble()
        val distance = kotlin.math.sqrt(dx * dx + dz * dz)

        val biomeIndex = pickBiomeIndex(generateCtx.worldContext.seed, neighborCellX, neighborCellZ, biomes.size)
        val biome = biomes[biomeIndex]

        candidates.add(CandidateBiome(biome, distance))
      }
    }

    candidates.sortBy { it.distance }

    val nearest = candidates[0]
    val second = candidates[1]
    val third = candidates[2]

    // distance-to-edge metric (still useful for "tame at borders")
    val distanceToEdgeBlocks = kotlin.math.abs(second.distance - nearest.distance)

    val edgeContext = BiomeEdgeContext.biomeEdgeContext(
      distanceToEdgeBlocks = distanceToEdgeBlocks,
      blendRadiusBlocks = blendRadiusBlocks
    )

    // Soft weights: avoids sudden "dominant flip" near the midpoint
    // Larger blendRadius -> smoother transitions
    val softness = (blendRadiusBlocks * 0.75).coerceAtLeast(1.0)

    fun weightFromDistance(distance: Double): Double {
      // Subtract nearest distance so we don't underflow
      val delta = distance - nearest.distance
      return kotlin.math.exp(-delta / softness)
    }

    val w1 = weightFromDistance(nearest.distance)
    val w2 = weightFromDistance(second.distance)
    val w3 = weightFromDistance(third.distance)

    //val total = (w1 + w2 + w3).coerceAtLeast(1e-9)

    val merged = linkedMapOf<Biome, Double>()
    for ((b, w) in listOf(nearest.biome to w1, second.biome to w2, third.biome to w3)) {
      merged[b] = (merged[b] ?: 0.0) + w
    }
    val totalMerged = merged.values.sum().coerceAtLeast(1e-9)

    val weighted = merged.entries.map { (b, w) ->
      WeightedBiome.weightedBiome(b, w / totalMerged)
    }

    val uniqueCount = merged.size
    val edgeCtx = if (uniqueCount <= 1) {
      BiomeEdgeContext.biomeEdgeContext(distanceToEdgeBlocks = Double.POSITIVE_INFINITY, blendRadiusBlocks = blendRadiusBlocks)
    } else {
      edgeContext
    }


    return BiomeBlendSample.biomeBlendSample(
      weighted,
      /*weightedBiomes = listOf(
        WeightedBiome.weightedBiome(nearest.biome, w1 / total),
        WeightedBiome.weightedBiome(second.biome, w2 / total),
        WeightedBiome.weightedBiome(third.biome, w3 / total)
      ),*/
      edgeContext = edgeCtx
    )
  }

  private data class CellPoint(val worldX: Int, val worldZ: Int)

  private fun cellPoint(seed: Long, cellX: Int, cellZ: Int, cellSizeBlocks: Int): CellPoint {
    // Deterministic "random" offset within the cell, so centers aren't on a perfect grid
    val hash = hash2D(seed, cellX, cellZ)
    val offsetX = (hash and 0xFFFF).toInt() % cellSizeBlocks
    val offsetZ = ((hash ushr 16) and 0xFFFF).toInt() % cellSizeBlocks

    val baseWorldX = cellX * cellSizeBlocks
    val baseWorldZ = cellZ * cellSizeBlocks
    return CellPoint(baseWorldX + offsetX, baseWorldZ + offsetZ)
  }

  private fun pickBiomeIndex(seed: Long, cellX: Int, cellZ: Int, biomeCount: Int): Int {
    val hash = hash2D(seed xor HASH_SALT, cellX, cellZ)
    val positive = hash and Long.MAX_VALUE
    return (positive % biomeCount.toLong()).toInt()
  }
}
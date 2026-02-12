package killercreepr.cruxworldgen.test6.biome

import killercreepr.cruxworldgen.test6.context.GenerateContext
import killercreepr.cruxworldgen.test6.zone.Zone
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.math.abs

class BiomeRegistry(
  val biomes: List<Biome>
) {
  // How large each biome cell is (bigger = larger biome regions)
  var biomeCellSizeBlocks: Int = 256

  // How wide the transition band is (smaller = sharper borders)
  var blendRadiusBlocks: Double = 32.0

  fun sampleBiomeBlend(generateCtx: GenerateContext, worldX: Int, worldZ: Int): BiomeBlendSample {
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

    val edgeContext = BiomeEdgeContext(
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

    val total = (w1 + w2 + w3).coerceAtLeast(1e-9)

    return BiomeBlendSample(
      weightedBiomes = listOf(
        WeightedBiome(nearest.biome, w1 / total),
        WeightedBiome(second.biome,  w2 / total),
        WeightedBiome(third.biome,   w3 / total)
      ),
      edgeContext = edgeContext
    )
  }


  /*fun sampleBiomeBlend(generateCtx: GenerateContext, worldX: Int, worldZ: Int): BiomeBlendSample {
    val cellX = floor(worldX.toDouble() / biomeCellSizeBlocks.toDouble()).toInt()
    val cellZ = floor(worldZ.toDouble() / biomeCellSizeBlocks.toDouble()).toInt()

    data class Candidate(val biome: Biome, val distance: Double)

    val candidates = ArrayList<Candidate>(25)

    // Use 5x5 to avoid missing the true 2nd/3rd nearest when jittered
    for (neighborCellX in (cellX - 2)..(cellX + 2)) {
      for (neighborCellZ in (cellZ - 2)..(cellZ + 2)) {

        val center = cellPoint(generateCtx.worldContext.seed, neighborCellX, neighborCellZ, biomeCellSizeBlocks)

        val dx = (worldX - center.worldX).toDouble()
        val dz = (worldZ - center.worldZ).toDouble()
        val distance = kotlin.math.sqrt(dx * dx + dz * dz)

        val biomeIndex = pickBiomeIndex(generateCtx.worldContext.seed, neighborCellX, neighborCellZ, biomes.size)
        val biome = biomes[biomeIndex]

        candidates.add(Candidate(biome, distance))
      }
    }

    // Sort by distance
    candidates.sortBy { it.distance }

    val nearest = candidates[0]
    val second = candidates[1]
    val third = candidates[2]

    // Edge distance is still useful (difference between 1st and 2nd)
    val distanceToEdge = kotlin.math.abs(second.distance - nearest.distance)
    val edgeContext = BiomeEdgeContext(
      distanceToEdgeBlocks = distanceToEdge,
      blendRadiusBlocks = blendRadiusBlocks
    )

    // Soft weights (no snapping). Smaller blendRadius -> sharper falloff.
    // k controls sharpness: bigger k = harder borders
    val k = 1.0 / blendRadiusBlocks.coerceAtLeast(1.0)

    fun weightFromDistance(distance: Double): Double {
      // Shift by nearest so numbers stay sane
      val shifted = distance - nearest.distance
      return kotlin.math.exp(-k * shifted)
    }

    val w1 = weightFromDistance(nearest.distance)
    val w2 = weightFromDistance(second.distance)
    val w3 = weightFromDistance(third.distance)

    val total = (w1 + w2 + w3).coerceAtLeast(1e-9)

    val weightedBiomes = listOf(
      WeightedBiome(nearest.biome, w1 / total),
      WeightedBiome(second.biome,  w2 / total),
      WeightedBiome(third.biome,   w3 / total)
    )

    return BiomeBlendSample(
      weightedBiomes = weightedBiomes,
      edgeContext = edgeContext
    )
  }*/


  private fun smoothstep01(value: Double): Double {
    val t = value.coerceIn(0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)
  }

  // --- helper types + functions ---

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

  private val HASH_SALT: Long = -7046029254386353131L         // 0x9E3779B97F4A7C15
  private val HASH_MUL_X: Long =  7145483588892929177L         // 0x632BE59BD9B4E019
  private val HASH_MIX_1: Long = -4658895280553007687L         // 0xBF58476D1CE4E5B9
  private val HASH_MIX_2: Long = -7723592293110705685L         // 0x94D049BB133111EB

  private fun pickBiomeIndex(seed: Long, cellX: Int, cellZ: Int, biomeCount: Int): Int {
    val hash = hash2D(seed xor HASH_SALT, cellX, cellZ)
    val positive = hash and Long.MAX_VALUE
    return (positive % biomeCount.toLong()).toInt()
  }

  private fun hash2D(seed: Long, x: Int, z: Int): Long {
    var value = seed
    value = value xor (x.toLong() * HASH_MUL_X)
    value = value xor (z.toLong() * HASH_SALT)
    value = (value xor (value ushr 30)) * HASH_MIX_1
    value = (value xor (value ushr 27)) * HASH_MIX_2
    return value xor (value ushr 31)
  }

}

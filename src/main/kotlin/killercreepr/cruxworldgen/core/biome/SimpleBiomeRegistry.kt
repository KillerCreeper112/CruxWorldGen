package killercreepr.cruxworldgen.core.biome

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.biome.BiomeRegistry
import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.generation.WeightedBiome
import killercreepr.cruxworldgen.api.util.HashUtil.HASH_SALT
import killercreepr.cruxworldgen.api.util.HashUtil.hash2D
import killercreepr.cruxworldgen.core.noise.BaseNoiseKeys
import net.minecraft.core.SectionPos.y
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sqrt

class SimpleBiomeRegistry(
  override val biomes: List<Biome>,
  override val biomeCellSizeBlocks: Int,
  override val blendRadiusBlocks: Double,
  val selector: CellBiomeSelector = WeightedRaritySelector(),
  val rules: BiomeRuleProvider = NoRules(),
  cacheCells: Int = 8192
) : BiomeRegistry {

  // --------- Biome blending (unchanged idea) ---------

  override fun sampleBiomeBlend(generateCtx: GenerateContext, worldX: Int, worldZ: Int): BiomeBlendSample {
    val seed = generateCtx.worldContext.seed
    val cellX = floor(worldX.toDouble() / biomeCellSizeBlocks.toDouble()).toInt()
    val cellZ = floor(worldZ.toDouble() / biomeCellSizeBlocks.toDouble()).toInt()

    data class Candidate(val biome: Biome, val distance: Double)
    val candidates = ArrayList<Candidate>(25)

    for (nx in (cellX - 2)..(cellX + 2)) {
      for (nz in (cellZ - 2)..(cellZ + 2)) {
        val center = cellPoint(seed, nx, nz, biomeCellSizeBlocks)

        val dx = (worldX - center.worldX).toDouble()
        val dz = (worldZ - center.worldZ).toDouble()
        val dist = sqrt(dx * dx + dz * dz)

        val biome = getCellBiome(seed, nx, nz)
        candidates.add(Candidate(biome, dist))
      }
    }

    candidates.sortBy { it.distance }
    val nearest = candidates[0]
    val second = candidates[1]
    val third = candidates[2]

    val distanceToEdgeBlocks = abs(second.distance - nearest.distance)

    val softness = (blendRadiusBlocks * 0.75).coerceAtLeast(1.0)
    fun w(distance: Double): Double {
      val delta = distance - nearest.distance
      return exp(-delta / softness)
    }

    val merged = linkedMapOf<Biome, Double>()
    for ((b, weight) in listOf(nearest.biome to w(nearest.distance), second.biome to w(second.distance), third.biome to w(third.distance))) {
      merged[b] = (merged[b] ?: 0.0) + weight
    }

    val total = merged.values.sum().coerceAtLeast(1e-9)
    val weighted = merged.entries.map { (b, weight) ->
      WeightedBiome.weightedBiome(b, weight / total)
    }

    val edgeCtx = if (merged.size <= 1) {
      BiomeEdgeContext.biomeEdgeContext(Double.POSITIVE_INFINITY, blendRadiusBlocks)
    } else {
      BiomeEdgeContext.biomeEdgeContext(distanceToEdgeBlocks, blendRadiusBlocks)
    }

    return BiomeBlendSample.biomeBlendSample(weighted, edgeContext = edgeCtx)
  }

  // --------- Predicate rules ---------

  /**
   * Context passed into biome predicates so they can make decisions.
   * IMPORTANT: neighbor lookups should generally use base picks to avoid recursive loops.
   */
  class RuleContext(
    val registry: SimpleBiomeRegistry,
    val seed: Long,
    val cellX: Int,
    val cellZ: Int,
    val rng: CellRng
  ) {
    /** Base (unconstrained) biome for *this* cell. */
    fun baseHere(): Biome = registry.getCellBiomeBase(seed, cellX, cellZ)

    /** Base (unconstrained) biome for a neighbor cell. */
    fun baseAt(dx: Int, dz: Int): Biome = registry.getCellBiomeBase(seed, cellX + dx, cellZ + dz)

    fun neighborsCardinal4(): List<Biome> = listOf(
      baseAt( 1, 0),
      baseAt(-1, 0),
      baseAt( 0, 1),
      baseAt( 0,-1)
    )

    fun neighborsMoore8(): List<Biome> = listOf(
      baseAt( 1, 0), baseAt(-1, 0), baseAt( 0, 1), baseAt( 0,-1),
      baseAt( 1, 1), baseAt( 1,-1), baseAt(-1, 1), baseAt(-1,-1)
    )
  }

  /**
   * A rule is a predicate: "Is it valid for this biome to exist at this cell?"
   * Return true = keep, false = replace.
   */
  fun interface BiomeRule {
    fun isValid(ctx: RuleContext, biome: Biome): Boolean
  }

  /**
   * Provides a rule predicate per biome id (or null = no rule).
   * This keeps rules modular and data-driven.
   */
  interface BiomeRuleProvider {
    fun ruleFor(biome: Biome): BiomeRule?
  }

  class NoRules : BiomeRuleProvider {
    override fun ruleFor(biome: Biome): BiomeRule? = null
  }

  /**
   * Enforces predicates by validating the chosen biome; if invalid, uses a fallback strategy.
   */
  class PredicateConstraints(
    private val provider: BiomeRuleProvider,
    private val fallback: Fallback = Fallback.MAJORITY_NEIGHBOR,
    private val maxRerolls: Int = 6
  ) : CellConstraintPolicy {

    enum class Fallback {
      MAJORITY_NEIGHBOR,
      RANDOM_NEIGHBOR,
      REROLL_THEN_MAJORITY
    }

    override fun enforce(registry: SimpleBiomeRegistry, seed: Long, cellX: Int, cellZ: Int, base: Biome, rng: CellRng): Biome {
      val ctx = RuleContext(registry, seed, cellX, cellZ, rng)
      val rule = provider.ruleFor(base) ?: return base
      if (rule.isValid(ctx, base)) return base

      val neighbors = ctx.neighborsCardinal4()

      fun majorityNeighbor(): Biome? = neighbors.maxByOrNull { it.rarityWeight }

      fun randomNeighbor(): Biome? {
        if (neighbors.isEmpty()) return null
        val idx = (rng.next01() * neighbors.size.toDouble()).toInt().coerceIn(0, neighbors.lastIndex)
        return neighbors[idx]
      }

      return when (fallback) {
        Fallback.MAJORITY_NEIGHBOR -> majorityNeighbor() ?: base
        Fallback.RANDOM_NEIGHBOR -> randomNeighbor() ?: base
        Fallback.REROLL_THEN_MAJORITY -> {
          var attempt = 0
          while (attempt++ < maxRerolls) {
            val candidate = registry.selector.pick(registry, seed, cellX, cellZ, rng.fork(attempt.toLong()))
            val cRule = provider.ruleFor(candidate)
            if (cRule == null || cRule.isValid(ctx, candidate)) return candidate
          }
          majorityNeighbor() ?: base
        }
      }
    }
  }

  // --------- Pluggable selection + constraint interfaces ---------

  interface CellBiomeSelector {
    fun pick(registry: SimpleBiomeRegistry, seed: Long, cellX: Int, cellZ: Int, rng: CellRng): Biome
  }

  interface CellConstraintPolicy {
    fun enforce(registry: SimpleBiomeRegistry, seed: Long, cellX: Int, cellZ: Int, base: Biome, rng: CellRng): Biome
  }

  class NoConstraints : CellConstraintPolicy {
    override fun enforce(registry: SimpleBiomeRegistry, seed: Long, cellX: Int, cellZ: Int, base: Biome, rng: CellRng): Biome = base
  }

  // Default: weighted rarity
  class WeightedRaritySelector : CellBiomeSelector {
    override fun pick(registry: SimpleBiomeRegistry, seed: Long, cellX: Int, cellZ: Int, rng: CellRng): Biome {
      return registry.pickWeightedBiome(rng.next01())
    }
  }

  // --------- Rarity weights (plug into your Biome as you like) ---------

  interface HasRarityWeight { val rarityWeight: Double }

  private fun biomeWeight(b: Biome): Double = (b as? HasRarityWeight)?.rarityWeight ?: 1.0

  private val cumulativeWeights: DoubleArray = run {
    val arr = DoubleArray(biomes.size)
    var sum = 0.0
    for (i in biomes.indices) {
      sum += biomeWeight(biomes[i]).coerceAtLeast(0.0)
      arr[i] = sum
    }
    if (sum <= 0.0) for (i in biomes.indices) arr[i] = (i + 1).toDouble()
    arr
  }

  private val totalWeight: Double = cumulativeWeights.last()

  private fun pickWeightedBiome(r01: Double): Biome {
    val t = r01.coerceIn(0.0, 1.0) * totalWeight
    val idx = cumulativeWeights.binarySearch(t).let { if (it >= 0) it else -it - 1 }
      .coerceIn(0, biomes.lastIndex)
    return biomes[idx]
  }

  // --------- Caching + cell biome resolution ---------

  private val constraintsPolicy: CellConstraintPolicy =
    when (rules) {
      is NoRules -> NoConstraints()
      else -> PredicateConstraints(rules) // default fallback; customize if you want
    }

  private val cellCache = CellLruCache<Long, Biome>(capacity = cacheCells)

  private fun cacheKey(cellX: Int, cellZ: Int): Long =
    (cellX.toLong() shl 32) xor (cellZ.toLong() and 0xFFFFFFFFL)

  private fun getCellBiome(seed: Long, cellX: Int, cellZ: Int): Biome {
    val key = cacheKey(cellX, cellZ)
    cellCache.get(key)?.let { return it }

    val rng = CellRng(seed, cellX, cellZ)
    val base = selector.pick(this, seed, cellX, cellZ, rng)
    val final = constraintsPolicy.enforce(this, seed, cellX, cellZ, base, rng)

    cellCache.put(key, final)
    return final
  }

  internal fun getCellBiomeBase(seed: Long, cellX: Int, cellZ: Int): Biome {
    val rng = CellRng(seed xor -0x61C8864680B583EBL, cellX, cellZ)
    return selector.pick(this, seed, cellX, cellZ, rng)
  }

  // --------- Worley site point ---------

  private data class CellPoint(val worldX: Int, val worldZ: Int)

  private fun cellPoint(seed: Long, cellX: Int, cellZ: Int, cellSizeBlocks: Int): CellPoint {
    val hash = hash2D(seed, cellX, cellZ)
    val offsetX = (hash and 0xFFFF).toInt().floorMod(cellSizeBlocks)
    val offsetZ = ((hash ushr 16) and 0xFFFF).toInt().floorMod(cellSizeBlocks)

    val baseWorldX = cellX * cellSizeBlocks
    val baseWorldZ = cellZ * cellSizeBlocks
    return CellPoint(baseWorldX + offsetX, baseWorldZ + offsetZ)
  }

  // --------- Deterministic RNG ---------

  class CellRng(seed: Long, cellX: Int, cellZ: Int) {
    private var state: Long = hash2D(seed xor HASH_SALT, cellX, cellZ)

    fun nextLong(): Long {
      var x = state
      x = x xor (x ushr 12)
      x = x xor (x shl 25)
      x = x xor (x ushr 27)
      state = x
      return x * 2685821657736338717L
    }

    fun next01(): Double {
      val v = nextLong() and Long.MAX_VALUE
      return v.toDouble() / Long.MAX_VALUE.toDouble()
    }

    fun fork(extra: Long): CellRng {
      val r = CellRng(0L, 0, 0)
      r.state = hash2D(state xor extra, (state ushr 32).toInt(), state.toInt())
      return r
    }
  }

  // --------- LRU ---------

  private class CellLruCache<K, V>(private val capacity: Int) {
    private val map = object : LinkedHashMap<K, V>(capacity, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean = size > capacity
    }
    @Synchronized fun get(key: K): V? = map[key]
    @Synchronized fun put(key: K, value: V) { map[key] = value }
  }

  private fun Int.floorMod(mod: Int): Int {
    val m = this % mod
    return if (m < 0) m + mod else m
  }
}

/*
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
}*/

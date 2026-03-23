package killercreepr.cruxworldgen.api.biome

import killercreepr.cruxworldgen.api.util.HashUtil.HASH_SALT
import killercreepr.cruxworldgen.api.util.HashUtil.hash2D
import killercreepr.cruxworldgen.core.biome.SimpleBiomeRegistry

/**
 * A rule is a predicate: "Is it valid for this biome to exist at this cell?"
 * Return true = keep, false = replace.
 */
fun interface BiomeRule {
  fun isValid(ctx: RuleContext, biome: Biome): Boolean

  class AnyNeighbour(val filter: (Biome) -> Boolean) : BiomeRule {
    override fun isValid(
      ctx: RuleContext,
      biome: Biome
    ): Boolean {
      for (biome in ctx.neighborsCardinal4()) {
        if (filter.invoke(biome)) return true
      }
      return false
    }
  }

  class AtLeastCardinalNeighbour(val min: Int, val filter: (Biome) -> Boolean) : BiomeRule {
    override fun isValid(
      ctx: RuleContext,
      biome: Biome
    ): Boolean {
      var i = 0
      for (biome in ctx.neighborsCardinal4()) {
        if (filter.invoke(biome)){
          i++
          if(i >= min) return true
        }
      }
      return false
    }
  }
}

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

interface BiomeRuleHolder{
  val biomeRule: BiomeRule?
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
  val provider: BiomeRuleProvider,
  val fallback: Fallback = Fallback.MAJORITY_NEIGHBOR,
  val maxRerolls: Int = 6
) : CellConstraintPolicy {

  enum class Fallback {
    MAJORITY_NEIGHBOR,
    RANDOM_NEIGHBOR,
    REROLL_THEN_MAJORITY
  }

  override fun enforce(
    registry: SimpleBiomeRegistry,
    seed: Long,
    cellX: Int,
    cellZ: Int,
    base: Biome,
    rng: CellRng
  ): Biome {
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

interface CellBiomeSelector {
  fun pick(registry: SimpleBiomeRegistry, seed: Long, cellX: Int, cellZ: Int, rng: CellRng): Biome
}

interface CellConstraintPolicy {
  fun enforce(registry: SimpleBiomeRegistry, seed: Long, cellX: Int, cellZ: Int, base: Biome, rng: CellRng): Biome
}

class NoConstraints : CellConstraintPolicy {
  override fun enforce(
    registry: SimpleBiomeRegistry,
    seed: Long,
    cellX: Int,
    cellZ: Int,
    base: Biome,
    rng: CellRng
  ): Biome = base
}
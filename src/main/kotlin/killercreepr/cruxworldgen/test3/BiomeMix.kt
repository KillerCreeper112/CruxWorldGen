package killercreepr.cruxworldgen.test3

import kotlin.collections.map

data class BiomeMix(
  val a: CruxBiome, val wa: Double, val coreA: Double,
  val b: CruxBiome, val wb: Double, val coreB: Double,
  val gwa: Double, val gwb: Double
) {
  val dominantBiome: CruxBiome get() = if (wa >= wb) a else b
  val dominantWeight: Double get() = maxOf(wa, wb)
  val border: Double get() = 4.0 * wa * wb
}

/*data class BiomeMix(
  val a: CruxBiome, val wa: Double, val coreA: Double,
  val b: CruxBiome, val wb: Double, val coreB: Double
){
  val confidence: Double get() = kotlin.math.abs(wa - wb) / (wa + wb).coerceAtLeast(1e-9)

  *//** 0..1, 1 = border (50/50), 0 = deep inside a biome *//*
  val border: Double get() = 4.0 * wa * wb

  val dominantBiome: CruxBiome get() = if (wa >= wb) a else b
  val dominantWeight: Double get() = maxOf(wa, wb)

}*/

fun smoothstep(edge0: Double, edge1: Double, x: Double): Double {
  val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
  return t * t * (3.0 - 2.0 * t)
}

class BiomeResolver(
  private val biomes: List<CruxBiome>,
  private val exponent: Double = 3.2,

  // core used for carvers/additives gating (keep separate)
  private val core0: Double = 0.60,
  private val core1: Double = 0.85,

  // NEW: geometry ownership
  private val geomSnap: Double = 0.68,     // tighten or loosen geometry mixing band
  private val geomPower: Double = 2.5      // higher = less minor-biome influence
) {
  fun mix(ctx: BiomeContext, x: Int, z: Int): BiomeMix {
    val scored = biomes.map { it to it.suitability(ctx, x, z).coerceAtLeast(0.0) }
      .sortedByDescending { it.second }

    val (a, sa) = scored[0]
    val (b, sb) = scored.getOrElse(1) { scored[0] }

    val pa = Math.pow(sa, exponent)
    val pb = Math.pow(sb, exponent)
    val sum = (pa + pb).coerceAtLeast(1e-9)

    val wa = pa / sum
    val wb = pb / sum

    val coreA = smoothstep(core0, core1, wa)
    val coreB = smoothstep(core0, core1, wb)

    // ---- geometry weights (ownership) ----
    val (gwa, gwb) = when {
      wa >= geomSnap -> 1.0 to 0.0
      wb >= geomSnap -> 0.0 to 1.0
      else -> {
        val ga = Math.pow(wa, geomPower)
        val gb = Math.pow(wb, geomPower)
        val gsum = (ga + gb).coerceAtLeast(1e-9)
        (ga / gsum) to (gb / gsum)
      }
    }

    return BiomeMix(a, wa, coreA, b, wb, coreB, gwa, gwb)
  }
}


/*class BiomeResolver(
  private val biomes: List<CruxBiome>,
  private val exponent: Double = 3.2,
  private val core0: Double = 0.3,
  private val core1: Double = 0.5
) {
  fun mix(ctx: BiomeContext, x: Int, z: Int): BiomeMix {
    val scored = biomes.map { it to it.suitability(ctx, x, z).coerceAtLeast(0.0) }
      .sortedByDescending { it.second }

    val (a, sa) = scored[0]
    val (b, sb) = scored.getOrElse(1) { scored[0] }

    val pa = Math.pow(sa, exponent)
    val pb = Math.pow(sb, exponent)
    val sum = (pa + pb).coerceAtLeast(1e-9)

    val wa = pa / sum
    val wb = pb / sum

    val coreA = smoothstep(core0, core1, wa)
    val coreB = smoothstep(core0, core1, wb)

    return BiomeMix(a, wa, coreA, b, wb, coreB)
  }
}*/

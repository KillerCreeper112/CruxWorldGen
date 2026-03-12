package killercreepr.cruxworldgen.api.feature

import killercreepr.cruxworldgen.api.context.LimitedRegion
import java.util.*

interface HeightSampler {
  companion object{
    fun relative(
      frac: Double
    ) = RelativeHeightSampler(frac)
  }
  fun sampleY(
    rng: Random,
    region : LimitedRegion,
    wx : Int,
    wz : Int
  ): Int
}

class RelativeHeightSampler(
  val frac: Double
) : HeightSampler {
  override fun sampleY(
    rng: Random,
    region: LimitedRegion,
    wx: Int,
    wz: Int
  ): Int {
    val minY = region.regionBounds.minY
    val maxY = region.regionBounds.maxY
    val span = (maxY - minY).coerceAtLeast(1)

    return (minY + span * frac).toInt()
  }
}

/** Uniform between */
class UniformHeight(val baseHeight : UniformHeightSampler) : HeightSampler {
  override fun sampleY(
    rng: Random,
    region: LimitedRegion,
    wx: Int,
    wz: Int
  ): Int {
    val lo = baseHeight.sampleMinY(rng, region, wx, wz)
    val hi = baseHeight.sampleMaxY(rng, region, wx, wz)
    if (hi < lo) return lo
    return lo + rng.nextInt(hi - lo + 1)
  }
}

/** Triangle distribution peaking at center */
class TriangleHeight(val baseHeight : UniformHeightSampler) : HeightSampler {
  override fun sampleY(
    rng: Random,
    region: LimitedRegion,
    wx: Int,
    wz: Int
  ): Int {
    val lo = baseHeight.sampleMinY(rng, region, wx, wz)
    val hi = baseHeight.sampleMaxY(rng, region, wx, wz)
    if (hi < lo) return lo
    val a = rng.nextInt(hi - lo + 1)
    val b = rng.nextInt(hi - lo + 1)
    return lo + (a + b) / 2
  }
}

/** Trapezoid-ish: flat middle, fades at edges (very useful for “wide band” ores) */
class TrapezoidHeight(val baseHeight : UniformHeightSampler,
                      val plateau: Int) : HeightSampler {
  override fun sampleY(
    rng: Random,
    region : LimitedRegion,
    wx : Int,
    wz : Int
  ): Int {
    val lo = baseHeight.sampleMinY(rng, region, wx, wz)
    val hi = baseHeight.sampleMaxY(rng, region, wx, wz)
    if (hi < lo) return lo

    val range = hi - lo + 1
    val p = plateau.coerceIn(0, range)
    // pick from [0..range+p) then clamp => creates a plateau in the middle
    val t = rng.nextInt(range + p)
    val v = (t - p / 2).coerceIn(0, range - 1)
    return lo + v
  }
}
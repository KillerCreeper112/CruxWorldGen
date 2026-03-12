package killercreepr.cruxworldgen.api.feature

import killercreepr.cruxworldgen.api.context.LimitedRegion
import java.util.*
import kotlin.math.absoluteValue

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

/** Triangle distribution peaking at center
 *  order=1 → uniform (no taper)
 *  order=2 → triangle (current default)
 *  order=4+ → tight bell, fast falloff
 */
class TriangleHeight(val baseHeight : UniformHeightSampler, val order: Int = 2) : HeightSampler {
  override fun sampleY(
    rng: Random,
    region: LimitedRegion,
    wx: Int,
    wz: Int
  ): Int {
    /*val lo = baseHeight.sampleMinY(rng, region, wx, wz)
    val hi = baseHeight.sampleMaxY(rng, region, wx, wz)
    if (hi < lo) return lo
    val a = rng.nextInt(hi - lo + 1)
    val b = rng.nextInt(hi - lo + 1)
    return lo + (a + b) / order*/

    val lo = baseHeight.sampleMinY(rng, region, wx, wz)
    val hi = baseHeight.sampleMaxY(rng, region, wx, wz)
    if (hi < lo) return lo
    val range = hi - lo + 1
    var sum = 0
    repeat(order) { sum += rng.nextInt(range) }
    return lo + sum / order
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

/** Like TriangleHeight but the distribution peaks at lo rather than center.
 *
 *  0 = uniform
 *  > 0 = peak at hi
 *  < 0 = peak at lo
 */
class SkewedHeight(
  val baseHeight: UniformHeightSampler,
  val order: Int = -2
) : HeightSampler {
  val sampledOrder = if(order > 0) order+1
  else order-1

  override fun sampleY(
    rng: Random,
    region: LimitedRegion,
    wx: Int,
    wz: Int
  ): Int {
    val lo = baseHeight.sampleMinY(rng, region, wx, wz)
    val hi = baseHeight.sampleMaxY(rng, region, wx, wz)
    if (hi < lo) return lo
    if(order == 0) return lo + rng.nextInt(hi - lo + 1)

    val range = hi - lo + 1
    var min = range - 1
    repeat(sampledOrder.absoluteValue) { min = minOf(min, rng.nextInt(range)) }
    return if (sampledOrder > 0) lo + (range - 1 - min) else lo + min
  }
}
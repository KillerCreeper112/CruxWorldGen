package killercreepr.cruxworldgen.api.feature

import killercreepr.cruxworldgen.api.context.LimitedRegion
import java.util.*

interface UniformHeightSampler{
  companion object{
    fun relative(
      minFrac: Double,
      maxFrac: Double
    ) = RelativeHeight(minFrac, maxFrac)
  }

  fun sampleMinY(
    rng: Random,
    region : LimitedRegion,
    wx : Int,
    wz : Int
  ): Int
  fun sampleMaxY(
    rng: Random,
    region : LimitedRegion,
    wx : Int,
    wz : Int
  ): Int

  fun isWithinRange(rng: Random,
                    region: LimitedRegion,
                    wy : Int) : Boolean
}

/**
 * If value is 0, will be at the bottom of the world,
 * If value is 1, will be at the top of the world.
 *
 * minFrac = 0-1
 * maxFrac = 0-1
 *
 * Example:
 * minFrac = 0.25 (25% above min height)
 * maxFrac = 0.75 (25% below max height)
 */
class RelativeHeight(
  val minFrac: Double,
  val maxFrac: Double
) : UniformHeightSampler {
  override fun sampleMinY(
    rng: Random,
    region: LimitedRegion,
    wx: Int,
    wz: Int
  ): Int {
    val minY = region.regionBounds.minY
    val maxY = region.regionBounds.maxY
    val span = (maxY - minY).coerceAtLeast(1)

    return (minY + span * minFrac).toInt()
  }

  override fun sampleMaxY(
    rng: Random,
    region: LimitedRegion,
    wx: Int,
    wz: Int
  ): Int {
    val minY = region.regionBounds.minY
    val maxY = region.regionBounds.maxY
    val span = (maxY - minY).coerceAtLeast(1)

    return (minY + span * maxFrac).toInt()
  }

  override fun isWithinRange(
    rng: Random,
    region: LimitedRegion,
    wy: Int
  ): Boolean = wy in sampleMinY(rng, region, 0, 0)..sampleMaxY(rng, region, 0,0)
}

/** Like TriangleHeight but the distribution peaks at lo rather than center.
 *  Useful for ores that get more common toward the top (emeralds) or
 *  bottom (ancient debris) of their range.
 *  flipped=false → peak at lo (bottom of range)
 *  flipped=true  → peak at hi (top of range)
 */
class SkewedHeight(
  val baseHeight: UniformHeightSampler,
  val order: Int = 2,
  val flipped: Boolean = false
) : HeightSampler {
  override fun sampleY(
    rng: Random,
    region: LimitedRegion,
    wx: Int,
    wz: Int
  ): Int {
    val lo = baseHeight.sampleMinY(rng, region, wx, wz)
    val hi = baseHeight.sampleMaxY(rng, region, wx, wz)
    if (hi < lo) return lo
    val range = hi - lo + 1
    var min = range - 1
    repeat(order) { min = minOf(min, rng.nextInt(range)) }
    return if (flipped) lo + (range - 1 - min) else lo + min
  }
}
package killercreepr.cruxworldgen.test.decor

import killercreepr.crux.api.data.Holder
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.decor.Decoration
import killercreepr.cruxworldgen.api.decor.DecorationPass
import killercreepr.cruxworldgen.api.decor.Placement
import killercreepr.cruxworldgen.api.decor.PropPoint
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.util.HashUtil.chance
import killercreepr.cruxworldgen.api.util.HashUtil.chooseInt
import killercreepr.cruxworldgen.api.util.HashUtil.mixSeed
import org.bukkit.Axis
import kotlin.math.abs

// ===========================================================================
// 2) Split-Heart Trees (Rot Cleft)
//    Trunk with a vertical “cleft” (missing column) filled with fungi blocks.
// ===========================================================================
class SplitHeartTreeDecor(
  override val pass: DecorationPass = DecorationPass.SURFACE,

  val chancePerPoint: Double = 0.06,
  val minAirAbove: Int = 9,
  val maxSlope01: Double = 0.75,

  val minHeight: Int = 8,
  val maxHeight: Int = 16,

  val minThickness: Int = 2,    // 2 or 3 recommended
  val maxThickness: Int = 3,

  val cleftWidth: Int = 1,      // usually 1
  val cleftDepth: Int = 1,      // how “deep” into trunk the cleft reads (1 works well)
  val cleftNoiseChance: Double = 0.25, // makes cleft jagged/organic

  val capChance: Double = 0.65,
  val capRadiusMin: Int = 2,
  val capRadiusMax: Int = 3,

  val logPicker: (Axis) -> BlockData,
  val leafPicker: Holder<BlockData>,
  val rotFill: Holder<BlockData>
) : Decoration {

  override fun shouldTry(region: LimitedRegion, point: PropPoint, biomeBlend: BiomeBlendSample): Boolean {
    val s = mixSeed(region.ctx.worldContext.seed, point.worldX, 0, point.worldZ, salt = 0x5392)
    return chance(s, chancePerPoint)
  }

  override fun findPlacement(region: LimitedRegion, point: PropPoint, biomeBlend: BiomeBlendSample): Placement? {
    val x0 = point.worldX
    val z0 = point.worldZ
    val t2d = region.terrainSnapshot.terrain2D
    val q = region.terrainQueries

    val surfaceY = t2d.surfaceY(x0, z0)
    val baseY = surfaceY + 1

    if (!region.isInRegion(x0, baseY, z0)) return null
    if (!q.isSolid(x0, surfaceY, z0)) return null
    if (t2d.isOceanColumn(x0, z0)) return null
    if (q.slope01(x0, z0) > maxSlope01) return null

    val airAbove = q.airBlocksAbove(x0, surfaceY, z0, maxCount = minAirAbove)
    if (airAbove < minAirAbove) return null

    val h = chooseInt(point.seed xor 0xA11CE, minHeight, maxHeight)
    val thick = chooseInt(point.seed xor 0x7123, minThickness, maxThickness)

    // cleft direction: along X or Z
    val cleftAxis = if (((point.seed ushr 33) and 1L) == 0L) Axis.X else Axis.Z

    // Validate volume for trunk
    val r = thick / 2
    for (dy in 0 until h) {
      val y = baseY + dy
      for (ox in -r..r) for (oz in -r..r) {
        val x = x0 + ox
        val z = z0 + oz
        if (!region.isInRegion(x, y, z)) return null
        // we allow replacing air/leaves/etc, but avoid punching into solid cliffs:
        if (!q.isReplaceable(x, y, z)) return null
      }
    }

    val doCap = chance(point.seed xor 0xCA94, capChance)
    val capR = chooseInt(point.seed xor 0xC421, capRadiusMin, capRadiusMax)

    return Placed(
      x0 = x0, z0 = z0, baseY = baseY,
      height = h, thick = thick,
      cleftAxis = cleftAxis,
      doCap = doCap, capR = capR,
      seed = point.seed
    )
  }

  override fun place(region: LimitedRegion, placement: Placement, biomeBlend: BiomeBlendSample) {
    val p = placement as Placed
    val q = region.terrainQueries
    val b = region.regionBounds

    val logY = logPicker(Axis.Y)
    val r = p.thick / 2

    // trunk volume with cleft
    for (dy in 0 until p.height) {
      val y = p.baseY + dy
      if (y < b.minY || y > b.maxY) break

      // cleft centerline offset (keeps cleft from being perfectly straight sometimes)
      val jitter = mixSeed(region.ctx.worldContext.seed, p.x0, y, p.z0, salt = p.seed xor 0xC1EF7L)
      val jag = chance(jitter, p.cleftNoiseChance)

      for (ox in -r..r) for (oz in -r..r) {
        val x = p.x0 + ox
        val z = p.z0 + oz
        if (!region.isInRegion(x, y, z)) continue
        if (!q.isReplaceable(x, y, z)) continue

        // define cleft cells (a slit down the trunk)
        val inCleft = when (p.cleftAxis) {
          Axis.X -> (abs(ox) < p.cleftWidth && (abs(oz) <= p.cleftDepth + (if (jag) 1 else 0)))
          Axis.Z -> (abs(oz) < p.cleftWidth && (abs(ox) <= p.cleftDepth + (if (jag) 1 else 0)))
          else -> false
        }

        if (inCleft) {
          // fill interior with rot/fungi (but keep some air holes)
          val hole = chance(mixSeed(region.ctx.worldContext.seed, x, y, z, salt = p.seed xor 0x9348), 0.18)
          if (!hole) region.setBlock(x, y, z, rotFill.value())
        } else {
          // outer bark
          region.setBlock(x, y, z, logY)
        }
      }
    }

    // cap (small sickly canopy)
    if (p.doCap) {
      val topY = p.baseY + p.height - 1
      val rr = p.capR
      for (ox in -rr..rr) for (oy in -1..1) for (oz in -rr..rr) {
        val dist2 = ox * ox + oz * oz + oy * oy
        if (dist2 > rr * rr + 1) continue
        val x = p.x0 + ox
        val y = topY + oy + 1
        val z = p.z0 + oz
        if (y < b.minY || y > b.maxY) continue
        if (!region.isInRegion(x, y, z)) continue
        if (!q.isReplaceable(x, y, z)) continue
        val s = mixSeed(region.ctx.worldContext.seed, x, y, z, salt = p.seed xor 0xC432)
        if (chance(s, 0.45)) region.setBlock(x, y, z, leafPicker.value())
      }
    }
  }

  data class Placed(
    val x0: Int,
    val z0: Int,
    val baseY: Int,
    val height: Int,
    val thick: Int,
    val cleftAxis: Axis,
    val doCap: Boolean,
    val capR: Int,
    val seed: Long,
    val cleftNoiseChance: Double = 0.25,
    val cleftWidth: Int = 1,
    val cleftDepth: Int = 1
  ) : Placement
}
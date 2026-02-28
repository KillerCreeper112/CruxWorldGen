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

// ============================================================================
// 3) Glitch-Offset Tree
//    Vertical trunk, but every few blocks it “teleports” 1 block sideways.
// ============================================================================
class GlitchOffsetTreeDecor(
  override val pass: DecorationPass = DecorationPass.SURFACE,

  val chancePerPoint: Double = 0.10,
  val minAirAbove: Int = 9,
  val maxSlope01: Double = 0.9,

  val minHeight: Int = 9,
  val maxHeight: Int = 18,

  val minGlitchEvery: Int = 2,
  val maxGlitchEvery: Int = 4,

  val maxTotalOffset: Int = 3,   // keeps it from drifting too far
  val topHaloChance: Double = 0.65,
  val logPicker : (Axis) -> BlockData,
  val leafPicker : Holder<BlockData>,
) : Decoration {

  override fun shouldTry(region: LimitedRegion, point: PropPoint, biomeBlend: BiomeBlendSample): Boolean {
    val s = mixSeed(region.ctx.worldContext.seed, point.worldX, 0, point.worldZ, salt = 0x617921)
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

    val h = chooseInt(point.seed xor 0x5EEDL, minHeight, maxHeight)
    val every = chooseInt(point.seed xor 0x0FF5E7L, minGlitchEvery, maxGlitchEvery)

    // pick two perpendicular “glitch axes” so it can stutter sideways
    val dirIdx = ((point.seed ushr 29) and 3L).toInt()
    val (dxA, dzA) = when (dirIdx) {
      0 -> 1 to 0
      1 -> -1 to 0
      2 -> 0 to 1
      else -> 0 to -1
    }
    val (dxB, dzB) = -dzA to dxA

    // Validate
    var x = x0
    var z = z0
    var offX = 0
    var offZ = 0

    for (dy in 0 until h) {
      val y = baseY + dy
      if (!region.isInRegion(x, y, z)) return null
      if (!q.isReplaceable(x, y, z)) return null

      if (dy > 0 && dy % every == 0) {
        val r = mixSeed(region.ctx.worldContext.seed, x, y, z, salt = point.seed xor dy.toLong())
        val useA = ((r ushr 11) and 1L) == 0L
        val sign = if (((r ushr 12) and 1L) == 0L) 1 else -1
        val ddx = (if (useA) dxA else dxB) * sign
        val ddz = (if (useA) dzA else dzB) * sign

        if (abs(offX + ddx) <= maxTotalOffset && abs(offZ + ddz) <= maxTotalOffset) {
          offX += ddx
          offZ += ddz
          x = x0 + offX
          z = z0 + offZ
        }
      }
    }

    return Placed(
      startX = x0, startZ = z0, baseY = baseY,
      height = h, glitchEvery = every,
      dxA = dxA, dzA = dzA, dxB = dxB, dzB = dzB,
      seed = point.seed
    )
  }

  override fun place(region: LimitedRegion, placement: Placement, biomeBlend: BiomeBlendSample) {
    val p = placement as Placed
    val q = region.terrainQueries
    val b = region.regionBounds

    var x = p.startX
    var z = p.startZ
    var offX = 0
    var offZ = 0

    val logY = logPicker.invoke(Axis.Y)
    for (dy in 0 until p.height) {
      val y = p.baseY + dy
      if (y < b.minY || y > b.maxY) break
      if (region.isInRegion(x, y, z) && q.isReplaceable(x, y, z)) {
        region.setBlock(x, y, z, logY)
      }

      if (dy > 0 && dy % p.glitchEvery == 0) {
        val r = mixSeed(region.ctx.worldContext.seed, x, y, z, salt = p.seed xor dy.toLong())
        val useA = ((r ushr 11) and 1L) == 0L
        val sign = if (((r ushr 12) and 1L) == 0L) 1 else -1
        val ddx = (if (useA) p.dxA else p.dxB) * sign
        val ddz = (if (useA) p.dzA else p.dzB) * sign

        if (abs(offX + ddx) <= p.maxTotalOffset && abs(offZ + ddz) <= p.maxTotalOffset) {
          offX += ddx
          offZ += ddz
          x = p.startX + offX
          z = p.startZ + offZ
        }
      }
    }

    // Optional “halo” of sparse leaves at the top
    val topY = p.baseY + p.height - 1
    val doHalo = chance(p.seed xor 0x8321, p.topHaloChance)
    if (doHalo) {
      for (ox in -2..2) for (oz in -2..2) {
        if (ox == 0 && oz == 0) continue
        if (abs(ox) + abs(oz) > 3) continue
        val tx = x + ox
        val tz = z + oz
        val ty = topY
        if (ty < b.minY || ty > b.maxY) continue
        if (!region.isInRegion(tx, ty, tz)) continue
        if (!q.isReplaceable(tx, ty, tz)) continue
        val s = mixSeed(region.ctx.worldContext.seed, tx, ty, tz, salt = p.seed xor 0x515151L)
        if (chance(s, 0.25)) region.setBlock(tx, ty, tz, leafPicker.value())
      }
    }
  }

  data class Placed(
    val startX: Int,
    val startZ: Int,
    val baseY: Int,
    val height: Int,
    val glitchEvery: Int,
    val dxA: Int, val dzA: Int,
    val dxB: Int, val dzB: Int,
    val seed: Long,
    val maxTotalOffset: Int = 3,
    val topHaloChance: Double = 0.65
  ) : Placement
}
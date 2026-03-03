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
// 3) Blister Stumps (Gas Pods)
//    Short stump with “blister” pods around it (can double as hazard nodes).
// ===========================================================================
class BlisterStumpDecor(
  override val pass: DecorationPass = DecorationPass.SURFACE,

  val chancePerPoint: Double = 0.10,
  val minAirAbove: Int = 4,
  val maxSlope01: Double = 0.85,

  val stumpHMin: Int = 2,
  val stumpHMax: Int = 4,

  val podCountMin: Int = 2,
  val podCountMax: Int = 5,

  val podRadiusChance: Double = 0.30, // chance a pod is 2-wide instead of 1
  val podUpChance: Double = 0.55,     // pods can sit one block above ground
  val podSlimeChance: Double = 0.35,  // some pods get “goo” trailing

  val logPicker: (Axis) -> BlockData,
  val podBlock: Holder<BlockData>,
  val gooBlock: Holder<BlockData>,
  val chanceSalt: Long
) : Decoration {

  override fun shouldTry(region: LimitedRegion, point: PropPoint, biomeBlend: BiomeBlendSample): Boolean {
    val s = mixSeed(region.ctx.worldContext.seed, point.worldX, 0, point.worldZ, salt = chanceSalt)
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

    val stumpH = chooseInt(point.seed xor 0x57293, stumpHMin, stumpHMax)
    for (dy in 0 until stumpH) {
      val y = baseY + dy
      if (!region.isInRegion(x0, y, z0)) return null
      if (!q.isReplaceable(x0, y, z0)) return null
    }

    val pods = chooseInt(point.seed xor 0x0394, podCountMin, podCountMax)
    return Placed(x0, z0, baseY, stumpH, pods, point.seed)
  }

  override fun place(region: LimitedRegion, placement: Placement, biomeBlend: BiomeBlendSample) {
    val p = placement as Placed
    val q = region.terrainQueries
    val b = region.regionBounds

    val logY = logPicker(Axis.Y)

    // stump
    for (dy in 0 until p.stumpH) {
      val y = p.baseY + dy
      if (y < b.minY || y > b.maxY) break
      if (q.isReplaceable(p.x0, y, p.z0)) region.setBlock(p.x0, y, p.z0, logY)
    }

    // pods around stump (ring-ish)
    for (i in 0 until p.podCount) {
      val r = mixSeed(region.ctx.worldContext.seed, p.x0, p.baseY, p.z0, salt = p.seed xor i.toLong())

      // choose a nearby offset in a 2-block ring
      val ox = ((r ushr 8) % 5L).toInt() - 2
      val oz = ((r ushr 13) % 5L).toInt() - 2
      if (ox == 0 && oz == 0) continue
      if (abs(ox) + abs(oz) > 3) continue

      val x = p.x0 + ox
      val z = p.z0 + oz

      // y: either on ground+1 or slightly higher
      val up = chance(r ushr 1, p.podUpChance)
      val y = p.baseY + (if (up) 1 else 0)

      if (y < b.minY || y > b.maxY) continue
      if (!region.isInRegion(x, y, z)) continue
      if (!q.isReplaceable(x, y, z)) continue

      region.setBlock(x, y, z, podBlock.value())

      // sometimes make pod 2-wide (one neighbor)
      val wide = chance(r ushr 2, p.podRadiusChance)
      if (wide) {
        val nx = x + (if (((r ushr 20) and 1L) == 0L) 1 else -1)
        val nz = z + (if (((r ushr 21) and 1L) == 0L) 1 else -1)
        if (region.isInRegion(nx, y, nz) && q.isReplaceable(nx, y, nz)) {
          region.setBlock(nx, y, nz, podBlock.value())
        }
      }

      // goo trailing down
      val goo = chance(r ushr 3, p.podSlimeChance)
      if (goo) {
        val gy = y - 1
        if (gy >= b.minY && region.isInRegion(x, gy, z) && q.isReplaceable(x, gy, z)) {
          region.setBlock(x, gy, z, gooBlock.value())
        }
      }
    }
  }

  data class Placed(
    val x0: Int,
    val z0: Int,
    val baseY: Int,
    val stumpH: Int,
    val podCount: Int,
    val seed: Long,
    val podRadiusChance: Double = 0.30,
    val podUpChance: Double = 0.55,
    val podSlimeChance: Double = 0.35
  ) : Placement
}
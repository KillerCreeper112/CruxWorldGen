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
import killercreepr.cruxworldgen.bukkit.util.AxisUtil.axisFromDir
import org.bukkit.Axis
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ===========================================================================
// 1) Mangrove Tangles
//    Root-arches + low trunk(s). Designed for mire edges / damp flats.
// ===========================================================================
class MangroveTanglesDecor(
  override val pass: DecorationPass = DecorationPass.SURFACE,

  val chancePerPoint: Double = 0.07,
  val minAirAbove: Int = 5,
  val maxSlope01: Double = 0.8,

  val rootCountMin: Int = 3,
  val rootCountMax: Int = 6,
  val rootLenMin: Int = 4,
  val rootLenMax: Int = 9,

  val rootRiseChance: Double = 0.65,    // how often a root “arches” up then down
  val rootMaxRise: Int = 2,             // max arch height above ground-follow
  val rootMeanderChance: Double = 0.25, // small sideways bends

  val trunkHeightMin: Int = 4,
  val trunkHeightMax: Int = 8,

  val canopyChance: Double = 0.55,
  val canopyRadiusMin: Int = 2,
  val canopyRadiusMax: Int = 3,
  val canopyFillChance: Double = 0.55,

  val logPicker: (Axis) -> BlockData,
  val leafBlock: Holder<BlockData>,
) : Decoration {

  override fun shouldTry(region: LimitedRegion, point: PropPoint, biomeBlend: BiomeBlendSample): Boolean {
    val s = mixSeed(region.ctx.worldContext.seed, point.worldX, 0, point.worldZ, salt = 0x4D4E_4752 /*"MNGR"*/)
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

    val rootCount = chooseInt(point.seed xor 0xA11CE, rootCountMin, rootCountMax)
    val trunkH = chooseInt(point.seed xor 0xBEEFL, trunkHeightMin, trunkHeightMax)

    // Light validation: ensure trunk column is placeable
    for (dy in 0 until trunkH) {
      val y = baseY + dy
      if (!region.isInRegion(x0, y, z0)) return null
      if (!q.isReplaceable(x0, y, z0)) return null
    }

    // Precompute each root as a polyline of steps (dx,dz,dy) and store absolute points.
    val roots = ArrayList<RootPath>(rootCount)

    for (ri in 0 until rootCount) {
      val rSeed = point.seed xor (0x9E3779B97F4A7C1L * (ri + 1))
      val len = chooseInt(rSeed xor 0x1234, rootLenMin, rootLenMax)

      // root start around base (offset a bit so it feels like spreading)
      val startSide = ((rSeed ushr 28) and 3L).toInt()
      val (sx, sz) = when (startSide) {
        0 -> x0 + 1 to z0
        1 -> x0 - 1 to z0
        2 -> x0 to z0 + 1
        else -> x0 to z0 - 1
      }

      // initial direction away from trunk
      var dx = sx - x0
      var dz = sz - z0
      if (dx == 0 && dz == 0) dx = 1

      var x = sx
      var z = sz
      var y = baseY - 1 // roots anchor into ground
      var rise = 0
      val points = ArrayList<P3>(len + 1)

      for (i in 0 until len) {
        // follow terrain with small arch
        val groundY = t2d.surfaceY(x, z)
        val desired = groundY + 1

        // decide if arching up/down
        val rr = mixSeed(region.ctx.worldContext.seed, x, 0, z, salt = rSeed xor i.toLong())
        val doRise = chance(rr, rootRiseChance)
        if (doRise) {
          // push rise up a bit in first half, down in second
          val half = len / 2
          rise = when {
            i < half -> min(rootMaxRise, rise + 1)
            else -> max(0, rise - 1)
          }
        } else {
          // relax toward 0
          if (rise > 0) rise--
        }

        y = desired + rise

        // must be in region + placeable or we abort this whole placement (keeps tangles clean)
        if (!region.isInRegion(x, y, z)) return null
        if (!q.isReplaceable(x, y, z)) return null
        // also ensure support under root (unless it is arched)
        if (rise == 0 && !q.isSolid(x, y - 1, z)) return null

        points.add(P3(x, y, z))

        // maybe meander direction (90° turns)
        if (chance(rr ushr 1, rootMeanderChance)) {
          val left = ((rr ushr 16) and 1L) == 0L
          val ndx = if (left) -dz else dz
          val ndz = if (left) dx else -dx
          dx = ndx
          dz = ndz
        }

        x += dx
        z += dz
      }

      roots.add(RootPath(points))
    }

    val doCanopy = chance(point.seed xor 0xC0A0, canopyChance)
    val canopyR = chooseInt(point.seed xor 0xCA11, canopyRadiusMin, canopyRadiusMax)

    return Placed(
      x0 = x0, z0 = z0, baseY = baseY,
      trunkH = trunkH,
      roots = roots,
      doCanopy = doCanopy,
      canopyR = canopyR,
      seed = point.seed
    )
  }

  override fun place(region: LimitedRegion, placement: Placement, biomeBlend: BiomeBlendSample) {
    val p = placement as Placed
    val q = region.terrainQueries
    val b = region.regionBounds

    val logY = logPicker(Axis.Y)

    // roots first (so trunk can “sit” on them visually)
    for (root in p.roots) {
      val pts = root.points
      for (idx in pts.indices) {
        val (x, y, z) = pts[idx]
        if (y < b.minY || y > b.maxY) continue
        if (region.isInRegion(x, y, z) && q.isReplaceable(x, y, z)) {
          // orient root log along its local direction when possible
          val axis = if (idx + 1 < pts.size) {
            val nx = pts[idx + 1].x - x
            val nz = pts[idx + 1].z - z
            if (nx == 0 && nz == 0) Axis.Y else axisFromDir(nx, nz)
          } else Axis.Y
          region.setBlock(x, y, z, logPicker(axis))
        }
      }
    }

    // trunk
    for (dy in 0 until p.trunkH) {
      val y = p.baseY + dy
      if (y < b.minY || y > b.maxY) break
      if (q.isReplaceable(p.x0, y, p.z0)) {
        region.setBlock(p.x0, y, p.z0, logY)
      }
    }

    // optional low canopy
    if (p.doCanopy) {
      val topY = p.baseY + p.trunkH - 1
      val r = p.canopyR
      for (ox in -r..r) for (oz in -r..r) {
        val dist = abs(ox) + abs(oz)
        if (dist > r + 1) continue
        val x = p.x0 + ox
        val z = p.z0 + oz
        val y = topY + (if (dist <= 1) 1 else 0)
        if (y < b.minY || y > b.maxY) continue
        if (!region.isInRegion(x, y, z)) continue
        if (!q.isReplaceable(x, y, z)) continue

        val s = mixSeed(region.ctx.worldContext.seed, x, y, z, salt = p.seed xor 0xC4932)
        if (chance(s, p.canopyFillChance)) region.setBlock(x, y, z, leafBlock.value())
      }
    }
  }

  data class P3(val x: Int, val y: Int, val z: Int)
  data class RootPath(val points: List<P3>)

  data class Placed(
    val x0: Int,
    val z0: Int,
    val baseY: Int,
    val trunkH: Int,
    val roots: List<RootPath>,
    val doCanopy: Boolean,
    val canopyR: Int,
    val seed: Long,
    val canopyFillChance: Double = 0.55
  ) : Placement
}
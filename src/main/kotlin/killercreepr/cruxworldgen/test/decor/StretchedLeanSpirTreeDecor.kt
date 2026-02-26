package killercreepr.cruxworldgen.test.decor

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

// ============================================================================
// 1) Stretched Lean Spire Tree
//    Tall thin trunk with occasional sideways “pull” + sparse tuft crown.
// ============================================================================
class StretchedLeanSpireTreeDecor(
  override val pass: DecorationPass = DecorationPass.SURFACE,

  val chancePerPoint: Double = 0.08,
  val minAirAbove: Int = 14,
  val maxSlope01: Double = 0.6,

  val minHeight: Int = 14,
  val maxHeight: Int = 30,

  val minStepEvery: Int = 3,   // how often it shifts sideways
  val maxStepEvery: Int = 6,

  val tuftChance: Double = 0.75,
  val tuftRadiusMin: Int = 1,
  val tuftRadiusMax: Int = 2,
  val tuftFillChance: Double = 0.45, // lower = sparser tuft
  val logPicker : (Axis) -> BlockData,
  val leafPicker : () -> BlockData,
) : Decoration {

  override fun shouldTry(region: LimitedRegion, point: PropPoint, biomeBlend: BiomeBlendSample): Boolean {
    val s = mixSeed(region.ctx.worldContext.seed, point.worldX, 0, point.worldZ, salt = 0x51_59392)
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

    val h = chooseInt(point.seed xor 0xA113_7EEDL, minHeight, maxHeight)
    val stepEvery = chooseInt(point.seed xor 0xBADA_55L, minStepEvery, maxStepEvery)

    // pick lean direction N/E/S/W
    val dirIdx = ((point.seed ushr 33) and 3L).toInt()
    val (dx, dz) = when (dirIdx) {
      0 -> 1 to 0
      1 -> -1 to 0
      2 -> 0 to 1
      else -> 0 to -1
    }

    // Validate: ensure every trunk spot is placeable and within bounds
    var x = x0
    var z = z0
    var shift = 0

    for (dy in 0 until h) {
      val y = baseY + dy
      if (!region.isInRegion(x, y, z)) return null
      if (!q.isReplaceable(x, y, z)) return null

      // shift sideways occasionally (the “pull”)
      if (dy > 0 && (dy % stepEvery == 0) && shift < 6) { // cap lean so it doesn't run away
        shift++
        x = x0 + dx * shift
        z = z0 + dz * shift
      }
    }

    // Optional tuft area validation (light)
    val doTuft = chance(point.seed xor 0x7777L, tuftChance)
    val tuftR = chooseInt(point.seed xor 0x1234_5678L, tuftRadiusMin, tuftRadiusMax)

    return Placed(
      startX = x0, startZ = z0, baseY = baseY,
      dx = dx, dz = dz,
      height = h, stepEvery = stepEvery,
      doTuft = doTuft, tuftR = tuftR,
      seed = point.seed
    )
  }

  override fun place(region: LimitedRegion, placement: Placement, biomeBlend: BiomeBlendSample) {
    val p = placement as Placed
    val q = region.terrainQueries
    val b = region.regionBounds

    var x = p.startX
    var z = p.startZ
    var shift = 0

    val axisY = Axis.Y
    val logY = logPicker(axisY)

    // trunk
    for (dy in 0 until p.height) {
      val y = p.baseY + dy
      if (y < b.minY || y > b.maxY) break

      if (q.isReplaceable(x, y, z)) {
        region.setBlock(x, y, z, logY)
      }

      if (dy > 0 && (dy % p.stepEvery == 0) && shift < 6) {
        shift++
        x = p.startX + p.dx * shift
        z = p.startZ + p.dz * shift
      }
    }

    if (!p.doTuft) return

    // sparse tuft crown at top
    val topY = p.baseY + p.height - 1
    val r = p.tuftR
    for (ox in -r..r) for (oy in -r..r) for (oz in -r..r) {
      val dist2 = ox * ox + oy * oy + oz * oz
      if (dist2 > r * r) continue

      val tx = x + ox
      val ty = topY + oy
      val tz = z + oz
      if (ty < b.minY || ty > b.maxY) continue
      if (!region.isInRegion(tx, ty, tz)) continue
      if (!q.isReplaceable(tx, ty, tz)) continue

      val s = mixSeed(region.ctx.worldContext.seed, tx, ty, tz, salt = p.seed xor 0x29301)
      if (chance(s, p.tuftFillChance)) {
        region.setBlock(tx, ty, tz, leafPicker.invoke())
      }
    }
  }

  data class Placed(
    val startX: Int,
    val startZ: Int,
    val baseY: Int,
    val dx: Int,
    val dz: Int,
    val height: Int,
    val stepEvery: Int,
    val doTuft: Boolean,
    val tuftR: Int,
    val seed: Long,
    val tuftFillChance: Double = 0.45
  ) : Placement
}
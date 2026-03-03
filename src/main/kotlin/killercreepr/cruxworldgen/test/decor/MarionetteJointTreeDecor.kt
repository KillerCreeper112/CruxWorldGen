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
import kotlin.math.max

// ============================================================================
// 2) Marionette Joint Tree
//    Trunk grows in segments; each segment end “kinks” direction; drooping limbs.
// ============================================================================
class MarionetteJointTreeDecor(
  override val pass: DecorationPass = DecorationPass.SURFACE,

  val chancePerPoint: Double = 0.07,
  val minAirAbove: Int = 10,
  val maxSlope01: Double = 0.75,

  val minHeight: Int = 10,
  val maxHeight: Int = 22,

  val minJointCount: Int = 2,
  val maxJointCount: Int = 4,

  val minSegLen: Int = 3,
  val maxSegLen: Int = 7,

  val minBranchLen: Int = 2,
  val maxBranchLen: Int = 5,
  val logPicker : (Axis) -> BlockData,
  val leafPicker : Holder<BlockData>,
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

    val height = chooseInt(point.seed xor 0x317823, minHeight, maxHeight)
    val joints = chooseInt(point.seed xor 0x32813, minJointCount, maxJointCount)

    // Precompute segment plan (lengths + turn decisions)
    val segLens = IntArray(joints + 1)
    var total = 0
    for (i in segLens.indices) {
      val len = chooseInt(point.seed xor (0x51L * (i + 1)), minSegLen, maxSegLen)
      segLens[i] = len
      total += len
    }
    // scale down/up to match target height roughly
    val scale = height.toDouble() / max(1, total).toDouble()
    for (i in segLens.indices) segLens[i] = max(2, (segLens[i] * scale).toInt())
    // ensure at least height
    var sum = segLens.sum()
    if (sum < height) segLens[segLens.lastIndex] += (height - sum)

    // Validate placement for trunk + some branch room (light check)
    var x = x0
    var z = z0
    var y = baseY
    var dx = 0
    var dz = 0

    // start vertical
    for (seg in segLens.indices) {
      val len = segLens[seg]
      for (i in 0 until len) {
        if (!region.isInRegion(x, y, z)) return null
        if (!q.isReplaceable(x, y, z)) return null
        y++
      }

      // at joint: choose a new direction for next segment (except after last)
      if (seg != segLens.lastIndex) {
        val r = mixSeed(region.ctx.worldContext.seed, x, y, z, salt = point.seed xor seg.toLong())
        val mode = (r % 10L).toInt()
        val turn = when {
          mode < 2 -> 0 // keep
          mode < 8 -> 1 // left/right
          else -> 2     // reverse
        }

        // if first direction not set, pick one
        if (dx == 0 && dz == 0) {
          val d = ((r ushr 8) and 3L).toInt()
          val (ndx, ndz) = when (d) {
            0 -> 1 to 0
            1 -> -1 to 0
            2 -> 0 to 1
            else -> 0 to -1
          }
          dx = ndx; dz = ndz
        } else if (turn == 1) {
          val left = ((r ushr 16) and 1L) == 0L
          val ndx = if (left) -dz else dz
          val ndz = if (left) dx else -dx
          dx = ndx; dz = ndz
        } else if (turn == 2) {
          dx = -dx; dz = -dz
        }

        // apply “kink” shift: step sideways once at the joint
        x += dx
        z += dz
      }
    }

    return Placed(
      startX = x0, startZ = z0, baseY = baseY,
      segLens = segLens,
      seed = point.seed
    )
  }

  override fun place(region: LimitedRegion, placement: Placement, biomeBlend: BiomeBlendSample) {
    val p = placement as Placed
    val q = region.terrainQueries
    val b = region.regionBounds

    var x = p.startX
    var z = p.startZ
    var y = p.baseY

    var dx = 0
    var dz = 0

    // trunk + joints
    for (seg in p.segLens.indices) {
      val len = p.segLens[seg]
      for (i in 0 until len) {
        if (y < b.minY || y > b.maxY) break
        if (q.isReplaceable(x, y, z)) {
          region.setBlock(x, y, z, logPicker(Axis.Y))
        }
        y++
      }

      // joint branches + kink shift (except after last)
      if (seg != p.segLens.lastIndex) {
        val r = mixSeed(region.ctx.worldContext.seed, x, y, z, salt = p.seed xor seg.toLong())

        // pick base direction if needed
        if (dx == 0 && dz == 0) {
          val d = ((r ushr 8) and 3L).toInt()
          val (ndx, ndz) = when (d) {
            0 -> 1 to 0
            1 -> -1 to 0
            2 -> 0 to 1
            else -> 0 to -1
          }
          dx = ndx; dz = ndz
        } else {
          val mode = (r % 10L).toInt()
          val turn = when {
            mode < 2 -> 0
            mode < 8 -> 1
            else -> 2
          }
          if (turn == 1) {
            val left = ((r ushr 16) and 1L) == 0L
            val ndx = if (left) -dz else dz
            val ndz = if (left) dx else -dx
            dx = ndx; dz = ndz
          } else if (turn == 2) {
            dx = -dx; dz = -dz
          }
        }

        // Emit 1–3 drooping limbs from the joint
        val limbCount = ((r ushr 24) and 3L).toInt().coerceIn(1, 3)
        for (li in 0 until limbCount) {
          val lr = mixSeed(region.ctx.worldContext.seed, x, y, z, salt = (p.seed xor (0xB11L * (seg + 1)) xor li.toLong()))
          val lenB = chooseInt(lr, minBranchLen, maxBranchLen)
          val left = ((lr ushr 12) and 1L) == 0L

          val bdx = if (left) -dz else dz
          val bdz = if (left) dx else -dx
          val bax = axisFromDir(bdx, bdz)

          var bx = x
          var bz = z
          var by = y - 1 // start a bit below joint

          for (step in 0 until lenB) {
            bx += bdx
            bz += bdz
            if (step % 2 == 1) by -= 1 // droop
            if (by < b.minY || by > b.maxY) break
            if (!region.isInRegion(bx, by, bz)) break
            if (!q.isReplaceable(bx, by, bz)) break
            region.setBlock(bx, by, bz, logPicker(bax))
          }

          // tiny leaf at tip (very sparse)
          if (region.isInRegion(bx, by, bz) && q.isReplaceable(bx, by + 1, bz)) {
            val tip = mixSeed(region.ctx.worldContext.seed, bx, by, bz, salt = p.seed xor 0x7129)
            if (chance(tip, 0.35)) region.setBlock(bx, by + 1, bz, leafPicker.value())
          }
        }

        // joint kink: step sideways once
        x += dx
        z += dz
      }
    }
  }

  data class Placed(
    val startX: Int,
    val startZ: Int,
    val baseY: Int,
    val segLens: IntArray,
    val seed: Long
  ) : Placement
}
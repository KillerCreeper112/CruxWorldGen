package killercreepr.cruxworldgen.test6.biome

import killercreepr.cruxworldgen.api.context.ChunkContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.decor.Decoration
import killercreepr.cruxworldgen.api.decor.DecorationPass
import killercreepr.cruxworldgen.api.decor.Placement
import killercreepr.cruxworldgen.api.decor.PropPoint
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import kotlin.math.max

class PlagueBubbleColumnsDecoration(
  override val pass: DecorationPass = DecorationPass.SURFACE,

  // patching (clusters)
  private val patchThreshold01: Double = 0.58, // higher = fewer patches
  private val chancePerPoint: Double = 0.35,   // within patch

  // placement constraints
  private val minWaterDepth: Int = 6,
  private val maxWaterDepth: Int = 22,

  // column shape
  private val minHeight: Int = 3,
  private val maxHeight: Int = 14,

  // keep inside chunk if you later add halo effects
  private val borderPadding: Int = 0
) : Decoration {

  private val SALT: Long = 0x1F2E3D4C5B6A798L

  override fun shouldTry(ctx: GenerateContext, point: PropPoint, biomeBlend: BiomeBlendSample): Boolean {
    val patch01 = 1.0//todo (ctx.noise.mireBubblePatch2D.noise(point.worldX.toDouble(), point.worldZ.toDouble()) + 1.0) * 0.5
    if (patch01 < patchThreshold01) return false

    val r01 = hash01(point.seed xor SALT)
    return r01 < chancePerPoint
  }

  override fun findPlacement(ctx: GenerateContext, point: PropPoint, biomeBlend: BiomeBlendSample): Placement? {
    val chunk = ctx.chunkContext
    val x = point.localX
    val z = point.localZ

    if (x !in borderPadding..(15 - borderPadding)) return null
    if (z !in borderPadding..(15 - borderPadding)) return null

    // Find a SOLID floor that has WATER above it (not “air surface”)
    val floorY = findWaterFloorY(chunk, x, z) ?: return null

    // Count how much contiguous water we have above floor
    val waterDepth = countWaterAbove(chunk, x, floorY, z, maxCount = maxWaterDepth + 2)
    //if (waterDepth < minWaterDepth) return null

    // Height varies by noise (stable, world-based)
    val var01 = 1.0//todo (ctx.noise.mireBubbleVar2D.noise(point.worldX.toDouble(), point.worldZ.toDouble()) + 1.0) * 0.5
    var height = lerpInt(minHeight, maxHeight, var01)

    // must fit inside the available water depth (leave top as water ok either way)
    height = height.coerceIn(1, max(1, waterDepth - 1))

    return BubbleColumnPlacement(x, z, floorY, height)
  }

  override fun place(ctx: GenerateContext, placement: Placement, biomeBlend: BiomeBlendSample) {
    val p = placement as BubbleColumnPlacement
    val chunk = ctx.chunkContext

    // Base for upward bubbles
    if (p.floorY in chunk.minHeight until chunk.maxHeight) {
      //todo chunk.setBlock(p.x, p.floorY, p.z, Material.SOUL_SAND)
    }

    // Column
    for (i in 1..p.height) {
      val y = p.floorY + i
      if (y !in chunk.minHeight until chunk.maxHeight) break

      // Only write into water (prevents punching holes into terrain)
      val current = chunk.getBlock(p.x, y, p.z)
      /* todo if (current == Material.WATER || current == Material.BUBBLE_COLUMN) {
        chunk.setBlock(p.x, y, p.z, Material.BUBBLE_COLUMN)
      } else {
        break
      }*/
    }
  }

  private fun findWaterFloorY(chunk: ChunkContext, x: Int, z: Int): Int? {
    val minY = chunk.minHeight
    val maxY = chunk.maxHeight - 1
    // Start near sea level and search downward
    val start = (maxY - 2).coerceAtMost(maxY - 2)

    for (y in start downTo (minY + 1)) {
      if (chunk.isSolid(x, y, z)) {
        //todo if(chunk.isEmpty(x, y + 1, z)) return y
      }
    }
    return null
  }

  private fun countWaterAbove(
    chunk: ChunkContext,
    x: Int,
    floorY: Int,
    z: Int,
    maxCount: Int
  ): Int {
    val maxY = chunk.maxHeight - 1
    var count = 0
    var y = floorY + 1
    while (y <= maxY && count < maxCount) {
      val mat = chunk.getBlock(x, y, z)
      //if (mat != Material.WATER && mat != Material.BUBBLE_COLUMN) break
      count++
      y++
    }
    return count
  }

  private fun lerpInt(a: Int, b: Int, t: Double): Int = (a + (b - a) * t).toInt()

  private fun hash01(seed: Long): Double {
    var v = seed
    v = (v xor (v ushr 30)) * -4658895280553007687L
    v = (v xor (v ushr 27)) * -7723592293110705685L
    v = v xor (v ushr 31)
    val u = (v and Long.MAX_VALUE).toDouble() / Long.MAX_VALUE.toDouble()
    return u
  }
}

data class BubbleColumnPlacement(
  val x: Int,
  val z: Int,
  val floorY: Int,
  val height: Int
) : Placement

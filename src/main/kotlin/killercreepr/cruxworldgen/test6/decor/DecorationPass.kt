package killercreepr.cruxworldgen.test6.decor

import killercreepr.cruxworldgen.test6.biome.BiomeBlendSample
import killercreepr.cruxworldgen.test6.context.ChunkContext
import killercreepr.cruxworldgen.test6.context.GenerateContext
import killercreepr.cruxworldgen.test6.prop.PropPoint
import org.bukkit.Material
import kotlin.math.pow

enum class DecorationPass {
  UNDERGROUND,
  SURFACE,
  POST_SURFACE
}

interface Decoration {
  val pass: DecorationPass

  /** Controls distribution. Examples: grid spacing, noise chance, biome-weight scaling */
  fun shouldTry(ctx: GenerateContext, point: PropPoint, biomeBlend: BiomeBlendSample): Boolean

  /** Pattern scan: find an anchor/placement candidate */
  fun findPlacement(ctx: GenerateContext, point: PropPoint, biomeBlend: BiomeBlendSample): Placement?

  /** Apply: place blocks using placement info */
  fun place(ctx: GenerateContext, placement: Placement, biomeBlend: BiomeBlendSample)
}

interface Placement

class DripstoneDecoration(
  override val pass: DecorationPass = DecorationPass.UNDERGROUND,

  private val patchThreshold01: Double = 0.55,   // higher = fewer patches
  private val chancePerPoint: Double = 0.45,     // within patch
  private val minGap: Int = 10,                  // need open space
  private val maxGap: Int = 90,
  private val minDepthBelowSurface: Int = 18,

  private val minSpikeHeight: Int = 3,
  private val maxSpikeHeight: Int = 14,

  private val minBaseRadius: Double = 1.2,
  private val maxBaseRadius: Double = 2.8,

  private val taperPower: Double = 1.6,          // cone shape (higher = sharper)
  private val connectDistance: Int = 2           // if stalactite+stalagmite nearly touch => pillar connect
) : Decoration {

  override fun shouldTry(ctx: GenerateContext, point: PropPoint, biomeBlend: BiomeBlendSample): Boolean {
    // Patch gate using world coords
    val patch01 = (ctx.noise.pillar2D(point.worldX, point.worldZ) + 1.0) * 0.5
    if (patch01 < patchThreshold01) return false

    // Deterministic chance per point
    val r01 = hash01(point.seed xor 0x51D11A7L)
    return r01 < chancePerPoint
  }

  override fun findPlacement(ctx: GenerateContext, point: PropPoint, biomeBlend: BiomeBlendSample): Placement? {
    val chunk = ctx.chunkContext
    val x = point.localX
    val z = point.localZ
    if (x !in 0..15 || z !in 0..15) return null

    val surfaceY = ctx.queries.surfaceY(x, z)
    val pocket = ctx.queries.findCavePocket(
      localX = x,
      localZ = z,
      surfaceY = surfaceY,
      minGap = minGap,
      maxGap = maxGap,
      searchDepthStartBelowSurface = 6
    ) ?: return null

    val depth = surfaceY - pocket.floorY
    if (depth < minDepthBelowSurface) return null

    // Variation from detail noise (stable, world-based)
    val detail01 = (ctx.noise.pillarHeight2D(point.worldX, point.worldZ) + 1.0) * 0.5

    val spikeHeight = lerpInt(minSpikeHeight, maxSpikeHeight, detail01)
    val baseRadius = lerp(minBaseRadius, maxBaseRadius, detail01)

    return DripstonePlacement(
      x = x, z = z,
      floorY = pocket.floorY,
      ceilY = pocket.ceilingY,
      spikeHeight = spikeHeight,
      baseRadius = baseRadius
    )
  }

  override fun place(ctx: GenerateContext, placement: Placement, biomeBlend: BiomeBlendSample) {
    val p = placement as DripstonePlacement
    val chunk = ctx.chunkContext

    val floorStart = p.floorY + 1
    val ceilStart = p.ceilY - 1

    // Place stalagmite (up)
    val stalagTop = (floorStart + p.spikeHeight).coerceAtMost(p.ceilY - 2)
    placeCone(
      ctx = ctx,
      cx = p.x, cz = p.z,
      y0 = floorStart,
      y1 = stalagTop,
      baseRadius = p.baseRadius,
      taperPower = taperPower,
      material = Material.DRIPSTONE_BLOCK
    )

    // Place stalactite (down)
    val stalacBottom = (ceilStart - p.spikeHeight).coerceAtLeast(p.floorY + 2)
    placeConeDown(
      ctx = ctx,
      cx = p.x, cz = p.z,
      y0 = ceilStart,
      y1 = stalacBottom,
      baseRadius = p.baseRadius,
      taperPower = taperPower,
      material = Material.DRIPSTONE_BLOCK
    )

    // If they almost meet, connect into a pillar
    if (stalacBottom - stalagTop <= connectDistance) {
      fillColumn(chunk, p.x, p.z, stalagTop, stalacBottom, Material.DRIPSTONE_BLOCK)
    }
  }

  private fun placeCone(
    ctx: GenerateContext,
    cx: Int, cz: Int,
    y0: Int, y1: Int,
    baseRadius: Double,
    taperPower: Double,
    material: Material
  ) {
    val h = (y1 - y0 + 1).coerceAtLeast(1)
    for (y in y0..y1) {
      val t = (y - y0).toDouble() / (h - 1).toDouble() // 0 at base, 1 at tip
      val r = baseRadius * (1.0 - t).pow(taperPower)
      paintDiskIfAir(ctx, cx, y, cz, r, material)
    }
  }

  private fun placeConeDown(
    ctx: GenerateContext,
    cx: Int, cz: Int,
    y0: Int, y1: Int,        // y0 is ceiling side, y1 is lower
    baseRadius: Double,
    taperPower: Double,
    material: Material
  ) {
    val h = (y0 - y1 + 1).coerceAtLeast(1)
    for (y in y0 downTo y1) {
      val t = (y0 - y).toDouble() / (h - 1).toDouble() // 0 at base, 1 at tip
      val r = baseRadius * (1.0 - t).pow(taperPower)
      paintDiskIfAir(ctx, cx, y, cz, r, material)
    }
  }

  private fun paintDiskIfAir(
    ctx: GenerateContext,
    cx: Int, y: Int, cz: Int,
    radius: Double,
    mat: Material,
    edgeJitterAmp: Double = 0.35 // try 0.2..0.6
  ) {
    val chunk = ctx.chunkContext
    if (y < chunk.minHeight || y >= chunk.maxHeight) return

    val rInt = kotlin.math.ceil(radius + edgeJitterAmp).toInt().coerceAtLeast(0)
    for (dx in -rInt..rInt) for (dz in -rInt..rInt) {
      val x = cx + dx
      val z = cz + dz
      if (x !in 0..15 || z !in 0..15) continue

      // jitter radius a bit to roughen silhouette
      val n = ctx.noise.detail3D(x, y, z) // [-1..1]
      val rEff = (radius + n * edgeJitterAmp).coerceAtLeast(0.0)
      val r2 = rEff * rEff

      val d2 = (dx * dx + dz * dz).toDouble()
      if (d2 > r2) continue

      if (chunk.isAir(x, y, z)) chunk.setBlock(x, y, z, mat)
    }
  }


  /*private fun paintDiskIfAir(chunk: ChunkContext, cx: Int, y: Int, cz: Int, radius: Double, mat: Material) {
    val rInt = kotlin.math.ceil(radius).toInt().coerceAtLeast(0)
    val r2 = radius * radius
    for (dx in -rInt..rInt) for (dz in -rInt..rInt) {
      val x = cx + dx
      val z = cz + dz
      if (x !in 0..15 || z !in 0..15) continue
      val d2 = (dx * dx + dz * dz).toDouble()
      if (d2 > r2) continue
      if (chunk.isAir(x, y, z)) chunk.setBlock(x, y, z, mat)
    }
  }*/

  private fun fillColumn(chunk: ChunkContext, x: Int, z: Int, yMin: Int, yMax: Int, mat: Material) {
    val a = minOf(yMin, yMax)
    val b = maxOf(yMin, yMax)
    for (y in a..b) if (chunk.isAir(x, y, z)) chunk.setBlock(x, y, z, mat)
  }

  private fun hash01(seed: Long): Double {
    var v = seed
    v = (v xor (v ushr 30)) * -4658895280553007687L
    v = (v xor (v ushr 27)) * -7723592293110705685L
    v = v xor (v ushr 31)
    val positive = v and Long.MAX_VALUE
    return positive.toDouble() / Long.MAX_VALUE.toDouble()
  }

  private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
  private fun lerpInt(a: Int, b: Int, t: Double): Int = (a + (b - a) * t).toInt()
}

data class DripstonePlacement(
  val x: Int,
  val z: Int,
  val floorY: Int,
  val ceilY: Int,
  val spikeHeight: Int,
  val baseRadius: Double
) : Placement

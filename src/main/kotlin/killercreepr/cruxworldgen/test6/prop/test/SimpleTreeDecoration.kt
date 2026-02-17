package killercreepr.cruxworldgen.test6.prop.test

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.context.ChunkContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.decor.Decoration
import killercreepr.cruxworldgen.api.decor.DecorationPass
import killercreepr.cruxworldgen.api.decor.Placement
import killercreepr.cruxworldgen.api.decor.PropPoint
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import org.bukkit.Material
import kotlin.math.abs

class SimpleTreeDecoration(
  override val pass: DecorationPass = DecorationPass.SURFACE,

  private val chancePerPoint: Double = 0.18,
  private val minAirAbove: Int = 7,
  private val maxSlope01: Double = 100.0,

  private val minHeight: Int = 4,
  private val maxHeight: Int = 7,

  // Prevent canopies clipping at chunk border (unless you implement cross-chunk block writes)
  private val borderPadding: Int = 1
) : Decoration {

  override fun shouldTry(region: LimitedRegion, point: PropPoint, biomeBlend: BiomeBlendSample): Boolean {
    // If you later add biome-specific toggles, put them here.
    // For now: deterministic chance gate.
    //val r01 = hash01(point.seed xor TREE_SALT)
    val ctx = region.ctx
    val r01 = CruxNoise.fast(ctx.worldContext.seed.toInt())
      .frequency(0.01)
      .noiseType(CruxNoise.NoiseType.OpenSimplex2)
      .fractalType(CruxNoise.FractalType.FBm)
      .fractalOctaves(1).noise(point.worldX.toDouble(), point.worldZ.toDouble())
    return r01 <= chancePerPoint
  }

  override fun findPlacement(region: LimitedRegion, point: PropPoint, biomeBlend: BiomeBlendSample): Placement? {
    val ctx = region.ctx
    val chunk = ctx.chunkContext

    val localX = point.localX
    val localZ = point.localZ

    // Must be inside chunk bounds + padding (avoid cross-chunk canopy writes)
    //if (localX !in borderPadding..(15 - borderPadding)) return null
    //if (localZ !in borderPadding..(15 - borderPadding)) return null

    val surfaceY = ctx.queries.surfaceY(localX, localZ)
    val baseY = surfaceY + 1

    if (ctx.queries.isUnderwater(surfaceY)) return null

    if (ctx.queries.slope01(localX, localZ) > maxSlope01) return null

    if (!chunk.isSolid(localX, surfaceY, localZ)) return null

// Need air above (LOCAL coords)
    val airAbove = ctx.queries.airBlocksAbove(localX, surfaceY, localZ, maxCount = 20)
    if (airAbove < minAirAbove) return null

    // Must have solid ground below
    if (surfaceY < chunk.minHeight || surfaceY >= chunk.maxHeight) return null
    if (!chunk.isSolid(localX, surfaceY, localZ)) return null

    // Need air above

    val height = chooseInt(point.seed xor 0x12345678L, minHeight, maxHeight)
    val canopyRadius = 2 + chooseInt(point.seed xor 0x9ABCDEF0L, 0, 1) // 2..3

    return TreePlacement(
      localX = localX,
      localZ = localZ,
      baseY = baseY,
      height = height,
      canopyRadius = canopyRadius,
      seed = point.seed
    )
  }

  override fun place(region: LimitedRegion, placement: Placement, biomeBlend: BiomeBlendSample) {
    val p = placement as TreePlacement
    val ctx = region.ctx
    val chunk = ctx.chunkContext

    // Trunk
    for (dy in 0 until p.height) {
      val y = p.baseY + dy
      if (y < chunk.minHeight || y >= chunk.maxHeight) break
      if (chunk.isEmpty(p.localX, y, p.localZ)) {
        chunk.setBlock(p.localX, y, p.localZ, BukkitBlockResolver.INSTANCE.resolve(Material.OAK_LOG))
      }
    }

    // Canopy around the top
    val topY = (p.baseY + p.height - 1).coerceAtMost(chunk.maxHeight - 1)
    placeCanopyBlob(chunk, p.localX, topY, p.localZ, p.canopyRadius, BukkitBlockResolver.INSTANCE.resolve(Material.OAK_LEAVES))
  }

  private fun placeCanopyBlob(
    chunk: ChunkContext,
    cx: Int,
    topY: Int,
    cz: Int,
    radius: Int,
    leaves: BlockData
  ) {
    // 3-layer blob: (topY-2..topY)
    for (dy in -2..0) {
      val y = topY + dy
      if (y < chunk.minHeight || y >= chunk.maxHeight) continue

      val r = when (dy) {
        0 -> radius - 1
        -1 -> radius
        else -> radius - 1
      }.coerceAtLeast(1)

      for (dx in -r..r) {
        for (dz in -r..r) {
          val x = cx + dx
          val z = cz + dz
          if (x !in 0..15 || z !in 0..15) continue//todo hard coded chunk borders

          val manhattan = abs(dx) + abs(dz)
          if (manhattan > r + 1) continue // soft corners

          if (chunk.isEmpty(x, y, z)) {
            chunk.setBlock(x, y, z, leaves)
          }
        }
      }
    }
  }

  private fun hash01(seed: Long): Double {
    var v = seed
    v = (v xor (v ushr 30)) * -4658895280553007687L
    v = (v xor (v ushr 27)) * -7723592293110705685L
    v = v xor (v ushr 31)
    val positive = v and Long.MAX_VALUE
    return positive.toDouble() / Long.MAX_VALUE.toDouble()
  }

  private fun chooseInt(seed: Long, min: Int, max: Int): Int {
    if (max <= min) return min
    val r = hash01(seed)
    return (min + (r * (max - min + 1)).toInt()).coerceIn(min, max)
  }
}

data class TreePlacement(
  val localX: Int,
  val localZ: Int,
  val baseY: Int,
  val height: Int,
  val canopyRadius: Int,
  val seed: Long
) : Placement

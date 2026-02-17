package killercreepr.cruxworldgen.test6.prop.test

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.block.BlockData
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
    //val chunk = ctx.chunkContext

    val worldX = point.worldX
    val worldZ = point.worldZ

    // Must be inside chunk bounds + padding (avoid cross-chunk canopy writes)
    //if (localX !in borderPadding..(15 - borderPadding)) return null
    //if (localZ !in borderPadding..(15 - borderPadding)) return null

    val terrain2D = region.terrainSnapshot.terrain2D

    val queries = region.terrainQueries
    val surfaceY = terrain2D.surfaceY(worldX, worldZ)
    val baseY = surfaceY + 1
    if(!region.isInRegion(worldX, baseY, worldZ)) return null
    if(!queries.isSolid(worldX, surfaceY, worldZ)) return null

    if(terrain2D.isOceanColumn(worldX, worldZ)) return null

    if (queries.slope01(worldX, worldZ) > maxSlope01) return null

    val airAbove = queries.airBlocksAbove(worldX, surfaceY, worldZ, maxCount = 20)
    if (airAbove < minAirAbove) return null

    val height = chooseInt(point.seed xor 0x12345678L, minHeight, maxHeight)
    val canopyRadius = 2 + chooseInt(point.seed xor 0x9ABCDEF0L, 0, 1) // 2..3
    return TreePlacement(
      worldX = worldX,
      worldZ = worldZ,
      baseY = baseY,
      height = height,
      canopyRadius = canopyRadius,
      seed = point.seed
    )
  }

  override fun place(region: LimitedRegion, placement: Placement, biomeBlend: BiomeBlendSample) {
    val p = placement as TreePlacement
    val queries = region.terrainQueries
    val bounds = region.regionBounds

    // Trunk
    for (dy in 0 until p.height) {
      val y = p.baseY + dy
      if (y < bounds.minY || y > bounds.maxY) break
      if (queries.isEmpty(p.worldX, y, p.worldZ)) {
        region.setBlock(p.worldX, y, p.worldZ, BukkitBlockResolver.INSTANCE.resolve(Material.OAK_LOG))
      }
    }

    // Canopy around the top
    val topY = (p.baseY + p.height - 1).coerceAtMost(bounds.maxY)
    placeCanopyBlob(region, p.worldX, topY, p.worldZ, p.canopyRadius, BukkitBlockResolver.INSTANCE.resolve(Material.OAK_LEAVES))
  }

  private fun placeCanopyBlob(
    region: LimitedRegion,
    cx: Int,
    topY: Int,
    cz: Int,
    radius: Int,
    leaves: BlockData
  ) {
    val bounds = region.regionBounds
    val queries = region.terrainQueries
    // 3-layer blob: (topY-2..topY)
    for (dy in -2..0) {
      val y = topY + dy
      if (y < bounds.minY || y > bounds.maxY) continue

      val r = when (dy) {
        0 -> radius - 1
        -1 -> radius
        else -> radius - 1
      }.coerceAtLeast(1)

      for (dx in -r..r) {
        for (dz in -r..r) {
          val x = cx + dx
          val z = cz + dz
          if (!region.isInRegion(x,y,z)) continue//todo hard coded chunk borders

          val manhattan = abs(dx) + abs(dz)
          if (manhattan > r + 1) continue // soft corners

          if (queries.isEmpty(x, y, z)) {
            region.setBlock(x, y, z, leaves)
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
  val worldX: Int,
  val worldZ: Int,
  val baseY: Int,
  val height: Int,
  val canopyRadius: Int,
  val seed: Long
) : Placement

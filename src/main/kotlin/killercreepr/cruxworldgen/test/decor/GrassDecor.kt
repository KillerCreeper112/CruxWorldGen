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
import killercreepr.cruxworldgen.api.util.HashUtil.mixSeed

class GrassDecor(
  override val pass: DecorationPass = DecorationPass.SURFACE,

  val chancePerPoint: Double = 0.18,
  val minAirAbove: Int = 7,
  val maxSlope01: Double = 1.0,
  val block : Holder<BlockData>,
  val salt: Long
) : Decoration {

  override fun shouldTry(region: LimitedRegion, point: PropPoint, biomeBlend: BiomeBlendSample): Boolean {
    val s = mixSeed(
      seed = region.ctx.worldContext.seed,
      x = point.worldX, y = 0, z = point.worldZ,
      salt = salt
    )
    return chance(s, chancePerPoint)
  }

  override fun findPlacement(region: LimitedRegion, point: PropPoint, biomeBlend: BiomeBlendSample): Placement? {
    val worldX = point.worldX
    val worldZ = point.worldZ

    val terrain2D = region.terrainSnapshot.terrain2D

    val queries = region.terrainQueries
    val surfaceY = terrain2D.surfaceY(worldX, worldZ)
    val baseY = surfaceY + 1
    if(!region.isInRegion(worldX, baseY, worldZ)) return null
    if(!queries.isSolid(worldX, surfaceY, worldZ)) return null

    if(terrain2D.isOceanColumn(worldX, worldZ)) return null

    if (maxSlope01 < 1.0 && queries.slope01(worldX, worldZ) > maxSlope01) return null

    val airAbove = queries.airBlocksAbove(worldX, surfaceY, worldZ, maxCount = minAirAbove)
    if (airAbove < minAirAbove) return null

    return Placed(
      worldX = worldX,
      worldZ = worldZ,
      baseY = baseY,
      seed = point.seed,
    )
  }

  override fun place(region: LimitedRegion, placement: Placement, biomeBlend: BiomeBlendSample) {
    val p = placement as Placed
    region.setBlock(p.worldX, p.baseY, p.worldZ, block.value())
  }
  data class Placed(
    val worldX: Int,
    val worldZ: Int,
    val baseY: Int,
    val seed: Long
  ) : Placement
}
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

class SingleDeadTreeDecor(
  override val pass: DecorationPass = DecorationPass.SURFACE,

  val chancePerPoint: Double = 0.18,
  val minAirAbove: Int = 7,
  val maxSlope01: Double = 100.0,

  val minHeight: Int = 4,
  val maxHeight: Int = 9,
  val log : Holder<BlockData>
) : Decoration {

  override fun shouldTry(region: LimitedRegion, point: PropPoint, biomeBlend: BiomeBlendSample): Boolean {
    val s = mixSeed(
      seed = region.ctx.worldContext.seed,
      x = point.worldX, y = 0, z = point.worldZ,
      salt = 1L
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

    if (queries.slope01(worldX, worldZ) > maxSlope01) return null

    val airAbove = queries.airBlocksAbove(worldX, surfaceY, worldZ, maxCount = minAirAbove)
    if (airAbove < minAirAbove) return null

    val height = chooseInt(point.seed xor 0x12345678L, minHeight, maxHeight)
    return Placed(
      worldX = worldX,
      worldZ = worldZ,
      baseY = baseY,
      height = height,
      seed = point.seed
    )
  }

  override fun place(region: LimitedRegion, placement: Placement, biomeBlend: BiomeBlendSample) {
    val p = placement as Placed
    val queries = region.terrainQueries
    val bounds = region.regionBounds

    for (dy in 0 until p.height) {
      val y = p.baseY + dy
      if (y < bounds.minY || y > bounds.maxY) break
      if (queries.isReplaceable(p.worldX, y, p.worldZ)) {
        region.setBlock(p.worldX, y, p.worldZ, log.value())
      }
    }
  }
  data class Placed(
    val worldX: Int,
    val worldZ: Int,
    val baseY: Int,
    val height: Int,
    val seed: Long
  ) : Placement
}
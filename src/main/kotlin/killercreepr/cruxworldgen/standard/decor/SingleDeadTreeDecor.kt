package killercreepr.cruxworldgen.standard.decor

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.decor.*
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.util.HashUtil

open class SingleDeadTreeDecor(
  override val pass: DecorationPass = DecorationPass.SURFACE,

  val chancePerPoint: Double = 0.18,
  val minAirAbove: Int = 7,
  val maxSlope01: Double = 100.0,

  val minHeight: Int = 4,
  val maxHeight: Int = 9,
  val log : BlockPicker,
  val chanceSalt: Long
) : VolumetricDecoration.LazyImpl {

  override fun shouldTry(
    region: LimitedRegion,
    point: VolumetricPropPoint,
    biomeBlend: BiomeBlendSample,
    biome: Biome
  ): Boolean {
    val s = HashUtil.mixSeed(
      seed = region.ctx.worldContext.seed,
      x = point.worldX, y = point.worldY, z = point.worldZ,
      salt = chanceSalt
    )
    return HashUtil.chance(s, chancePerPoint)
  }

  override fun shouldTry(region: LimitedRegion, point: PropPoint, biomeBlend: BiomeBlendSample): Boolean {
    val s = HashUtil.mixSeed(
      seed = region.ctx.worldContext.seed,
      x = point.worldX, z = point.worldZ,
      salt = chanceSalt
    )
    return HashUtil.chance(s, chancePerPoint)
  }

  /** Pattern scan: find an anchor/placement candidate */
  override fun findPlacement(
    region: LimitedRegion,
    point: VolumetricPropPoint,
    biomeBlend: BiomeBlendSample,
    biome: Biome
  ): Placement? {
    return findPlacement(region, point.worldX, point.worldY, point.worldZ, point.seed, biomeBlend)
  }

  fun findPlacement(
    region: LimitedRegion,
    worldX: Int,
    surfaceY: Int,
    worldZ: Int,
    seed: Long,
    biomeBlend: BiomeBlendSample
  ): Placement?{
    val terrain2D = region.terrainSnapshot.terrain2D

    val queries = region.terrainQueries
    val baseY = surfaceY + 1
    if(!region.isInRegion(worldX, baseY, worldZ)) return null
    if(!queries.isSolid(worldX, surfaceY, worldZ)) return null

    if(terrain2D.isOceanColumn(worldX, worldZ)) return null

    if (queries.slope01(worldX, worldZ) > maxSlope01) return null

    val airAbove = queries.airBlocksAbove(worldX, surfaceY, worldZ, maxCount = minAirAbove)
    if (airAbove < minAirAbove) return null

    val height = HashUtil.chooseInt(seed xor 0x12345678L, minHeight, maxHeight)
    return Placed(
      worldX = worldX,
      worldZ = worldZ,
      baseY = baseY,
      height = height,
      seed = seed
    )
  }

  override fun findPlacement(region: LimitedRegion, point: PropPoint, biomeBlend: BiomeBlendSample): Placement? {
    val worldX = point.worldX
    val worldZ = point.worldZ

    val terrain2D = region.terrainSnapshot.terrain2D

    val surfaceY = terrain2D.surfaceY(worldX, worldZ)
    return findPlacement(region, worldX, surfaceY, worldZ, point.seed, biomeBlend)
  }

  override fun place(region: LimitedRegion, placement: Placement, biomeBlend: BiomeBlendSample) {
    val p = placement as Placed
    val queries = region.terrainQueries
    val bounds = region.regionBounds

    for (dy in 0 until p.height) {
      val y = p.baseY + dy
      if (y < bounds.minY || y > bounds.maxY) break
      if (queries.isReplaceable(p.worldX, y, p.worldZ)) {
        val logBlock = log.pickBlock(region, p.worldX, y, p.worldZ) ?: continue
        region.setBlock(p.worldX, y, p.worldZ, logBlock)
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
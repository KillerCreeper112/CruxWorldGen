package killercreepr.cruxworldgen.standard.decor.volumetric

import killercreepr.crux.api.data.Holder
import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.decor.DecorationPass
import killercreepr.cruxworldgen.api.decor.Placement
import killercreepr.cruxworldgen.api.decor.VolumetricDecoration
import killercreepr.cruxworldgen.api.decor.VolumetricPropPoint
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.util.HashUtil.chance
import killercreepr.cruxworldgen.api.util.HashUtil.chooseInt
import killercreepr.cruxworldgen.api.util.HashUtil.mixSeed

class TallGrassTriVolDecor(
  override val pass: DecorationPass = DecorationPass.UNDERGROUND,

  val chancePerPoint: Double = 0.18,
  val maxSlope01: Double = 1.0,
  val minHeight: Int = 2,
  val maxHeight: Int = 3,
  val top : Holder<BlockData>,
  val middle : Holder<BlockData>,
  val bottom : Holder<BlockData>,
  val chanceSalt: Long
) : VolumetricDecoration {

  override fun shouldTry(region: LimitedRegion, point: VolumetricPropPoint, biomeBlend: BiomeBlendSample, biome: Biome): Boolean {
    val s = mixSeed(
      seed = region.ctx.worldContext.seed,
      x = point.worldX, y = 0, z = point.worldZ,
      salt = chanceSalt
    )
    return chance(s, chancePerPoint)
  }

  override fun findPlacement(region: LimitedRegion, point: VolumetricPropPoint, biomeBlend: BiomeBlendSample, biome: Biome): Placement? {
    val seed = point.seed
    val worldX = point.worldX
    val worldY = point.worldY
    val worldZ = point.worldZ

    val terrain2D = region.terrainSnapshot.terrain2D

    val queries = region.terrainQueries
    val baseY = worldY + 1
    if(!region.isInRegion(worldX, baseY, worldZ)) return null
    if(!queries.isSolid(worldX, worldY, worldZ)) return null

    if(terrain2D.isOceanColumn(worldX, worldZ)) return null

    if (maxSlope01 < 1.0 && queries.slope01(worldX, worldZ) > maxSlope01) return null

    val height = chooseInt(seed xor 3929L, minHeight, maxHeight)

    val airAbove = queries.airBlocksAbove(worldX, worldY, worldZ, maxCount = height)
    if (airAbove < height) return null

    return Placed(
      worldX = worldX,
      worldZ = worldZ,
      baseY = baseY,
      seed = point.seed,
      height = height,
    )
  }

  override fun place(region: LimitedRegion, placement: Placement, biomeBlend: BiomeBlendSample, biome: Biome) {
    val p = placement as Placed
    val height = p.height

    for(i in 0..<height) {
      val block = when (i) {
        0 -> bottom.value()
        height-1 -> top.value()
        else -> middle.value()
      }
      region.setBlock(p.worldX, p.baseY+i, p.worldZ, block)
    }
  }
  data class Placed(
    val worldX: Int,
    val worldZ: Int,
    val baseY: Int,
    val seed: Long,
    val height: Int
  ) : Placement
}
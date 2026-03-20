package killercreepr.cruxworldgen.standard.decor.volumetric

import killercreepr.crux.core.util.CruxMath
import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.block.BlockPicker
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.decor.DecorationPass
import killercreepr.cruxworldgen.api.decor.Placement
import killercreepr.cruxworldgen.api.decor.VolumetricDecoration
import killercreepr.cruxworldgen.api.decor.VolumetricPropPoint
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.util.HashUtil
import killercreepr.cruxworldgen.api.util.HashUtil.chance
import killercreepr.cruxworldgen.api.util.HashUtil.mixSeed

open class IcicleVolDecor(
  val chancePerPoint: Double = 0.5,
  val minAir: Int = 7,
  val block : BlockPicker,
  val minLength: Int = 7,
  val maxLength: Int = 25,
  val baseSizeMin: Int = 1,
  val baseSizeMax: Int = 6,

  val sizeTaperOffMin: Double = 0.3,
  val sizeTaperOffMax: Double = 0.6,

  val chanceSalt: Long = CruxMath.random().nextLong(),
  val yOffset: Int = -1
) : VolumetricDecoration {
  override val pass = DecorationPass.UNDERGROUND

  /** Controls distribution. Examples: grid spacing, noise chance, biome-weight scaling */
  override fun shouldTry(
    region: LimitedRegion,
    point: VolumetricPropPoint,
    biomeBlend: BiomeBlendSample,
    biome: Biome
  ): Boolean {
    val s = mixSeed(
      seed = region.ctx.worldContext.seed,
      x = point.worldX, y = point.worldY, z = point.worldZ,
      salt = chanceSalt
    )
    return chance(s, chancePerPoint)
  }

  /** Pattern scan: find an anchor/placement candidate */
  override fun findPlacement(
    region: LimitedRegion,
    point: VolumetricPropPoint,
    biomeBlend: BiomeBlendSample,
    biome: Biome
  ): Placement? {
    val worldX = point.worldX
    val worldY = point.worldY
    val worldZ = point.worldZ

    val queries = region.terrainQueries
    val baseY = worldY + yOffset
    if(!region.isInRegion(worldX, baseY, worldZ)) return null
    if(!queries.isSolid(worldX, worldY, worldZ)) return null
    if(!queries.isEmpty(worldX, baseY, worldZ)) return null

    val air = if(yOffset < 0) queries.airBlocksBelow(worldX, worldY, worldZ, maxCount = minAir)
    else queries.airBlocksAbove(worldX, worldY, worldZ, maxCount = minAir)
    if (air < minAir) return null

    return Placed(
      worldX = worldX,
      worldZ = worldZ,
      baseY = baseY,
      seed = point.seed,
    )
  }

  /** Apply: place blocks using placement info */
  override fun place(
    region: LimitedRegion,
    placement: Placement,
    biomeBlend: BiomeBlendSample,
    biome: Biome
  ) {
    val p = placement as Placed
    val queries = region.terrainQueries

    // Derive randomised dimensions from the placement seed
    var rng = mixSeed(p.seed, chanceSalt)

    val length = HashUtil.chooseInt(rng, minLength, maxLength)
    rng = mixSeed(rng, 7L)
    val baseSize = HashUtil.chooseInt(rng, baseSizeMin, baseSizeMax)

    val sizeTaperOff = HashUtil.chooseDouble(mixSeed(rng, 2389L), sizeTaperOffMin, sizeTaperOffMax)

    // Iterate every column within the bounding diamond
    for (dx in -baseSize..baseSize) {
      for (dz in -baseSize..baseSize) {
        val manhattanDist = Math.abs(dx) + Math.abs(dz)

        // Columns outside the base footprint are never part of the icicle
        if (manhattanDist > baseSize) continue

        val wx = p.worldX + dx
        val wz = p.worldZ + dz

        // Terrain adaptation: find the actual ceiling for this column.
        // The central column is guaranteed solid one block above baseY;
        // neighbouring columns may differ, so scan upward a short distance.
        var columnTopY = p.baseY
        for (offset in 1..(baseSize + 2)) {
          val checkY = p.baseY - (offset * yOffset)
          if (!region.isInRegion(wx, checkY, wz)) break
          if (queries.isSolid(wx, checkY, wz)) {
            columnTopY = checkY + yOffset   // hang from just below this solid block
            break
          }
        }

        // Grow downward, shrinking the radius with each step
        for (dy in 0 until length) {
          val progress = if (length > 1) dy.toDouble() / (length - 1) else 1.0

          // sizeTaperOff < 1  → stays wide longer, pointed tip  (classic icicle)
          // sizeTaperOff > 1  → narrows quickly from the base
          val radiusAtDepth = (baseSize.toDouble() * Math.pow(1.0 - progress, sizeTaperOff)).toInt()

          // Once this column falls outside the shrinking radius it will never
          // re-enter it (radius is monotonically decreasing), so stop early.
          if (manhattanDist > radiusAtDepth) break

          val worldY = columnTopY + (dy * yOffset)
          if (!region.isInRegion(wx, worldY, wz)) break

          // Don't overwrite existing blocks (other decorations, stone, etc.)
          if (!queries.isEmpty(wx, worldY, wz)) break

          val block = block.pickBlock(region, region.ctx.random, wx, worldY, wz) ?: break
          region.setBlock(wx, worldY, wz, block)
        }
      }
    }
  }

  data class Placed(
    val worldX: Int,
    val worldZ: Int,
    val baseY: Int,
    val seed: Long
  ) : Placement
}
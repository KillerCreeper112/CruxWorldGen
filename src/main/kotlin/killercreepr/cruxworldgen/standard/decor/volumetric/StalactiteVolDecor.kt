package killercreepr.cruxworldgen.standard.decor.volumetric

import killercreepr.crux.core.util.CruxMath
import killercreepr.cruxworldgen.api.block.BlockPicker
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.decor.Placement
import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.util.HashUtil
import killercreepr.cruxworldgen.api.util.HashUtil.chance
import killercreepr.cruxworldgen.api.util.HashUtil.mixSeed

class StalactiteVolDecor(
  chancePerPoint: Double = 0.4,
  minAir: Int = 8,
  block: BlockPicker,
  minLength: Int = 6,
  maxLength: Int = 20,
  baseSizeMin: Int = 1,
  baseSizeMax: Int = 5,
  sizeTaperOffMin: Double = 0.35,
  sizeTaperOffMax: Double = 0.65,

  /** How far along the diamond edge (0–1) erosion begins. 0 = erode everything, 1 = no erosion. */
  val edgeErosionStart: Double = 0.5,
  /** At the outermost edge, what fraction of columns survive. 0 = all eroded, 1 = none eroded. */
  val edgeErosionStrength: Double = 0.0,

  /** Max blocks a column's length can be shortened by. */
  val columnLengthShortMin: Int = 3,
  /** Max blocks a column's length can be extended by. */
  val columnLengthExtendMax: Int = 1,

  /** Magnitude of per-slice radius jitter. 0 = perfectly smooth taper, higher = lumpier sides. */
  val radiusJitter: Double = 0.6,

  chanceSalt: Long = CruxMath.random().nextLong(),
  yOffset: Int = -1
) : IcicleVolDecor(
  chancePerPoint, minAir, block,
  minLength, maxLength,
  baseSizeMin, baseSizeMax,
  sizeTaperOffMin, sizeTaperOffMax,
  chanceSalt, yOffset
) {

  override fun place(
    region: LimitedRegion,
    placement: Placement,
    biomeBlend: BiomeBlendSample,
    biome: Biome
  ) {
    val p = placement as Placed
    val queries = region.terrainQueries

    var rng = mixSeed(p.seed, chanceSalt)

    val length = HashUtil.chooseInt(rng, minLength, maxLength)
    rng = mixSeed(rng, 7L)
    val baseSize = HashUtil.chooseInt(rng, baseSizeMin, baseSizeMax)
    rng = mixSeed(rng, 2389L)
    val sizeTaperOff = HashUtil.chooseDouble(rng, sizeTaperOffMin, sizeTaperOffMax)

    for (dx in -baseSize..baseSize) {
      for (dz in -baseSize..baseSize) {
        val manhattanDist = Math.abs(dx) + Math.abs(dz)
        if (manhattanDist > baseSize) continue

        val wx = p.worldX + dx
        val wz = p.worldZ + dz

        val colSeed = mixSeed(p.seed, mixSeed(dx.toLong(), dz.toLong()))

        // Edge erosion
        val edgeFraction = manhattanDist.toDouble() / baseSize.coerceAtLeast(1)
        if (edgeFraction > edgeErosionStart) {
          val erosionProgress = (edgeFraction - edgeErosionStart) / (1.0 - edgeErosionStart)
          val survivalChance = 1.0 - erosionProgress * (1.0 - edgeErosionStrength)
          if (!chance(mixSeed(colSeed, 11L), survivalChance)) continue
        }

        // Per-column length variation
        val columnLength = (length + HashUtil.chooseInt(
          mixSeed(colSeed, 31L), -columnLengthShortMin, columnLengthExtendMax
        )).coerceAtLeast(1)

        var columnTopY = p.baseY
        for (offset in 1..(baseSize + 2)) {
          val checkY = p.baseY - (offset * yOffset)
          if (!region.isInRegion(wx, checkY, wz)) break
          if (queries.isSolid(wx, checkY, wz)) {
            columnTopY = checkY + yOffset
            break
          }
        }

        for (dy in 0 until columnLength) {
          val progress = if (columnLength > 1) dy.toDouble() / (columnLength - 1) else 1.0

          val baseRadius = baseSize.toDouble() * Math.pow(1.0 - progress, sizeTaperOff)
          val jitter = if (radiusJitter > 0.0)
            HashUtil.chooseDouble(mixSeed(colSeed, dy.toLong()), -radiusJitter, radiusJitter)
          else 0.0
          val radiusAtDepth = (baseRadius + jitter).toInt()

          if (manhattanDist > radiusAtDepth) break

          val worldY = columnTopY + (dy * yOffset)
          if (!region.isInRegion(wx, worldY, wz)) break
          if (!queries.isEmpty(wx, worldY, wz)) break

          val block = block.pickBlock(region, region.ctx.random, wx, worldY, wz) ?: break
          region.setBlock(wx, worldY, wz, block)
        }
      }
    }
  }
}
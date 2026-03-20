package killercreepr.cruxworldgen.standard.decor

import killercreepr.crux.core.util.CruxMath
import killercreepr.cruxworldgen.api.block.BlockGetter
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.decor.Decoration
import killercreepr.cruxworldgen.api.decor.DecorationPass
import killercreepr.cruxworldgen.api.decor.Placement
import killercreepr.cruxworldgen.api.decor.PropPoint
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.util.HashUtil
import killercreepr.cruxworldgen.api.util.HashUtil.chance
import killercreepr.cruxworldgen.api.util.HashUtil.mixSeed

open class BracketFungusDecor(
  val chancePerPoint: Double = 0.05,

  /** How far the bracket extends outward from the wall */
  val depthMin: Float = 2f,
  val depthMax: Float = 6f,
  /** Width of the bracket along the wall face */
  val widthMin: Float = 3f,
  val widthMax: Float = 8f,
  /** Vertical thickness of the bracket */
  val thicknessMin: Int = 1,
  val thicknessMax: Int = 3,

  /** How many tiers stack vertically — each tier is a separate bracket slightly smaller than the one above */
  val tierCountMin: Int = 1,
  val tierCountMax: Int = 4,
  /** Vertical gap between tiers */
  val tierSpacingMin: Int = 1,
  val tierSpacingMax: Int = 3,
  /** Each lower tier shrinks by this fraction relative to the one above */
  val tierShrinkMin: Double = 0.65,
  val tierShrinkMax: Double = 0.85,

  /** Upward curve of the bracket surface — 0 = flat slab, higher = curls up at the tip */
  val topCurvatureMin: Double = 0.3,
  val topCurvatureMax: Double = 1.2,
  /** How much the underside scoops concave — gives the shelf a lip */
  val undersideScoopMin: Double = 0.2,
  val undersideScoopMax: Double = 0.6,

  /** Lobing — splits the outer edge into rounded lobes */
  val lobeCountMin: Int = 0,
  val lobeCountMax: Int = 3,
  val lobeDepthMin: Double = 0.1,
  val lobeDepthMax: Double = 0.3,

  val capSectorCount: Int = 7,
  val capSectorStrength: Double = 0.12,
  val capColumnHeightJitter: Double = 0.0,
  val capEdgeErosionChance: Double = 0.0,

  /** Scan up this many blocks from the prop point to find a wall */
  val wallScanRange: Int = 4,

  val topBlock: BlockGetter,
  val bottomBlock: BlockGetter,

  val chanceSalt: Long = CruxMath.random().nextLong(),
  override val pass: DecorationPass = DecorationPass.SURFACE
) : Decoration {

  override fun shouldTry(
    region: LimitedRegion,
    point: PropPoint,
    biomeBlend: BiomeBlendSample
  ): Boolean {
    val s = mixSeed(region.ctx.worldContext.seed, point.worldX, point.worldZ, chanceSalt)
    return chance(s, chancePerPoint)
  }

  override fun findPlacement(
    region: LimitedRegion,
    point: PropPoint,
    biomeBlend: BiomeBlendSample
  ): Placement? {
    val queries = region.terrainQueries

    // Scan upward from prop point — brackets often appear mid-cliff
    val worldY = region.terrainSnapshot.terrain2D.surfaceY(point.worldX, point.worldZ)

    for (scanY in worldY until worldY + wallScanRange) {
      if (!region.isInRegion(point.worldX, scanY, point.worldZ)) continue

      // This block must be air for the bracket to grow into
      if (!queries.isEmpty(point.worldX, scanY, point.worldZ)) continue

      // Check all 4 cardinal faces for a solid wall
      for ((wallDx, wallDz) in CARDINALS) {
        val wallX = point.worldX + wallDx
        val wallZ = point.worldZ + wallDz
        if (!region.isInRegion(wallX, scanY, wallZ)) continue
        if (!queries.isSolid(wallX, scanY, wallZ)) continue

        // Need at least 2 air blocks outward so there's room to grow
        val outX = point.worldX - wallDx
        val outZ = point.worldZ - wallDz
        if (!region.isInRegion(outX, scanY, outZ)) continue
        if (!queries.isEmpty(outX, scanY, outZ)) continue

        return Placed(
          worldX = point.worldX,
          worldY = scanY,
          worldZ = point.worldZ,
          wallDx = wallDx,
          wallDz = wallDz,
          seed = point.seed
        )
      }
    }
    return null
  }

  override fun place(
    region: LimitedRegion,
    placement: Placement,
    biomeBlend: BiomeBlendSample
  ) {
    val p = placement as Placed
    var rng = mixSeed(p.seed, chanceSalt)

    val tierCount    = HashUtil.chooseInt(rng, tierCountMin, tierCountMax);       rng = mixSeed(rng, 3L)
    val tierSpacing  = HashUtil.chooseInt(rng, tierSpacingMin, tierSpacingMax);   rng = mixSeed(rng, 7L)
    val tierShrink   = HashUtil.chooseDouble(rng, tierShrinkMin, tierShrinkMax);  rng = mixSeed(rng, 11L)
    val lobeCount    = HashUtil.chooseInt(rng, lobeCountMin, lobeCountMax);       rng = mixSeed(rng, 13L)
    val lobeDepth    = HashUtil.chooseDouble(rng, lobeDepthMin, lobeDepthMax);    rng = mixSeed(rng, 17L)
    val topCurvature = HashUtil.chooseDouble(rng, topCurvatureMin, topCurvatureMax); rng = mixSeed(rng, 19L)
    val undersideScoop = HashUtil.chooseDouble(rng, undersideScoopMin, undersideScoopMax)

    // Outward direction — away from the wall
    val outDx = -p.wallDx.toDouble()
    val outDz = -p.wallDz.toDouble()

    // Perpendicular direction along the wall face
    val sideDx = p.wallDz.toDouble()
    val sideDz = -p.wallDx.toDouble()

    for (tier in 0 until tierCount) {
      val tierRng = mixSeed(rng, tier.toLong() * 31L)

      val scale = Math.pow(tierShrink, tier.toDouble())
      val depth     = HashUtil.chooseFloat(tierRng, depthMin, depthMax) * scale.toFloat()
      val halfWidth = HashUtil.chooseFloat(mixSeed(tierRng, 5L), widthMin, widthMax) * scale.toFloat() * 0.5f
      val thickness = HashUtil.chooseInt(mixSeed(tierRng, 9L), thicknessMin, thicknessMax)

      // Each tier starts lower — lowermost tier is the biggest, grows up
      val baseY = p.worldY - tier * (thickness + tierSpacing)

      // Sector multipliers for edge irregularity (applied in the outward/side plane)
      val sectorMultipliers = DoubleArray(capSectorCount) { sector ->
        HashUtil.chooseDouble(mixSeed(tierRng, sector.toLong() * 97L), 1.0 - capSectorStrength, 1.0 + capSectorStrength)
      }

      val scanRadius = (maxOf(depth, halfWidth) + 2).toInt()

      for (dx in -scanRadius..scanRadius) {
        for (dz in -scanRadius..scanRadius) {
          val bx = p.worldX + dx
          val bz = p.worldZ + dz

          if (!region.isInRegion(bx, baseY, bz)) continue

          // Project block offset into outward/side axes
          val outwardDist = dx * outDx + dz * outDz   // distance along growth direction
          val sideDist    = dx * sideDx + dz * sideDz  // distance along wall face

          // Only grow outward, not back into the wall
          if (outwardDist < 0.0) continue

          // Normalise into bracket space
          val normOut  = outwardDist / depth           // 0 at wall, 1 at tip
          val normSide = Math.abs(sideDist) / halfWidth // 0 at center, 1 at edge

          if (normOut > 1.0 || normSide > 1.0) continue

          // Semicircle footprint: tip rounds off using ellipse in out/side plane
          val footprint = normOut * normOut + normSide * normSide
          if (footprint > 1.0) continue

          // Lobe modulation along the outer edge — dents the perimeter
          val edgeDist = Math.sqrt(footprint)
          if (lobeCount > 0 && edgeDist > 0.55) {
            val lobeAngle = Math.atan2(normSide, normOut)
            val lobeWave = Math.cos(lobeAngle * lobeCount * 2) * lobeDepth
            if (footprint > (1.0 - lobeWave) * (1.0 - lobeWave)) continue
          }

          // Sector irregularity on the outer edge
          val angle = Math.atan2(normSide, normOut)
          val sector = (((angle / (Math.PI * 0.5) + 0.5) * capSectorCount).toInt()
            .coerceIn(0, capSectorCount - 1))
          if (normOut * sectorMultipliers[sector] + normSide > 1.3) continue

          // Edge erosion
          val colSeed = mixSeed(tierRng, mixSeed(bx.toLong() * 31L, bz.toLong() * 97L))
          if (edgeDist > 0.75) {
            val erosionProgress = (edgeDist - 0.75) / 0.25
            if (!chance(mixSeed(colSeed, 41L), 1.0 - erosionProgress * capEdgeErosionChance * 8.0)) continue
          }

          // Per-column height jitter
          val heightJitter = HashUtil.chooseDouble(
            mixSeed(colSeed, 53L), -thickness * capColumnHeightJitter, thickness * capColumnHeightJitter
          )

          // Top surface curvature — bracket curls upward toward the tip
          // At the wall (normOut=0) no curl, at the tip (normOut=1) maximum lift
          val topLift = (topCurvature * normOut * normOut * thickness).toInt()

          // Underside scoop — concave hollow on the bottom face near the tip
          val undersideDip = (undersideScoop * normOut * thickness).toInt()

          for (dy in -1..thickness + topLift) {
            val worldY = baseY + dy
            if (!region.isInRegion(bx, worldY, bz)) continue
            if (!region.terrainQueries.isEmpty(bx, worldY, bz)) continue

            val localDy = dy.toDouble()

            // Top surface: flat slab that curves upward toward the tip
            val topSurface = thickness + topLift + heightJitter
            if (localDy > topSurface) continue

            // Underside scoop: removes blocks from the bottom near the tip
            val bottomSurface = undersideDip.toDouble()
            if (localDy < bottomSurface && normOut > 0.4) continue

            val isTop = dy == (thickness + topLift)
            val block = if (isTop)
              topBlock.getBlock(region, region.ctx.random, bx, worldY, bz)
            else
              bottomBlock.getBlock(region, region.ctx.random, bx, worldY, bz)

            block ?: continue
            region.setBlock(bx, worldY, bz, block)
          }
        }
      }
    }
  }

  data class Placed(
    val worldX: Int,
    val worldY: Int,
    val worldZ: Int,
    val wallDx: Int,
    val wallDz: Int,
    val seed: Long
  ) : Placement

  companion object {
    val CARDINALS = listOf(
      1 to 0, -1 to 0, 0 to 1, 0 to -1
    )
  }
}
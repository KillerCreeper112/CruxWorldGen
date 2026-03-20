package killercreepr.cruxworldgen.standard.decor

import killercreepr.crux.core.util.CruxMath
import killercreepr.cruxworldgen.api.block.BlockPicker
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.decor.DecorationPass
import killercreepr.cruxworldgen.api.decor.Placement
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.noise.NoiseKey
import killercreepr.cruxworldgen.api.util.Curve
import killercreepr.cruxworldgen.api.util.GenUtil
import killercreepr.cruxworldgen.api.util.HashUtil
import killercreepr.cruxworldgen.api.util.HashUtil.chance
import killercreepr.cruxworldgen.api.util.HashUtil.mixSeed

open class RoundedRedMushroomDecor(
  chancePerPoint: Double = 0.04,

  stemHeightMin: Int = 8,
  stemHeightMax: Int = 18,
  stemRadiusMin: Float = 1.5f,
  stemRadiusMax: Float = 3.0f,
  stemWanderStrength: Float = 0.18f,

  capRadiusMin: Float = 6f,
  capRadiusMax: Float = 12f,

  /** Height of the dome above the underside at the center */
  val capDomeHeightMin: Float = 3f,
  val capDomeHeightMax: Float = 6f,
  /** Thickness of the cap shell — thicker = chunkier */
  val capThicknessMin: Int = 2,
  val capThicknessMax: Int = 4,

  /** How far the rim hangs below the underside as fraction of capRadius */
  val rimDropFractionMin: Double = 0.25,
  val rimDropFractionMax: Double = 0.50,
  /** Curl exponent — 1 = linear droop, 2+ = flat until edge then sharp curl */
  val rimCurlMin: Double = 1.8,
  val rimCurlMax: Double = 3.5,
  /** How much the dome blends toward flat at the center — 0 = pure cosine, 1 = flatter top */
  val domeFlattenMin: Double = 0.0,
  val domeFlattenMax: Double = 0.4,

  val capSectorCount: Int = 7,
  val capSectorStrength: Double = 0.15,
  val capColumnHeightJitter: Double = 0.0,
  val capEdgeErosionChance: Double = 0.0,

  stemNoiseStrength: Double = 0.9,
  stemNoise: NoiseKey,
  capNoise: NoiseKey,
  stemBlock: BlockPicker,
  capBlock: BlockPicker,
  chanceSalt: Long = CruxMath.random().nextLong(),
  pass: DecorationPass = DecorationPass.SURFACE
) : BrownMushroomDecor(
  chancePerPoint = chancePerPoint,
  stemHeightMin = stemHeightMin,
  stemHeightMax = stemHeightMax,
  stemRadiusMin = stemRadiusMin,
  stemRadiusMax = stemRadiusMax,
  stemWanderStrength = stemWanderStrength,
  capRadiusMin = capRadiusMin,
  capRadiusMax = capRadiusMax,
  capHeightScaleMin = 0.3f,
  capHeightScaleMax = 0.3f,
  capOverhangMin = 0f,
  capOverhangMax = 0f,
  stemNoiseStrength = stemNoiseStrength,
  capNoiseStrength = 0.0,
  stemNoise = stemNoise,
  capNoise = capNoise,
  stemBlock = stemBlock,
  capBlock = capBlock,
  chanceSalt = chanceSalt,
  pass = pass
) {

  override fun place(
    region: LimitedRegion,
    placement: Placement,
    biomeBlend: BiomeBlendSample
  ) {
    val p = placement as Placed
    var rng = mixSeed(p.seed, chanceSalt)

    val stemHeight   = HashUtil.chooseInt(rng, stemHeightMin, stemHeightMax);          rng = mixSeed(rng, 3L)
    val stemRadius   = HashUtil.chooseFloat(rng, stemRadiusMin, stemRadiusMax);        rng = mixSeed(rng, 7L)
    val capRadius    = HashUtil.chooseFloat(rng, capRadiusMin, capRadiusMax);          rng = mixSeed(rng, 11L)
    val capDomeHeight = HashUtil.chooseFloat(rng, capDomeHeightMin, capDomeHeightMax); rng = mixSeed(rng, 13L)
    val capThickness = HashUtil.chooseInt(rng, capThicknessMin, capThicknessMax);      rng = mixSeed(rng, 17L)
    val rimDrop      = HashUtil.chooseDouble(rng, rimDropFractionMin, rimDropFractionMax); rng = mixSeed(rng, 19L)
    val rimCurl      = HashUtil.chooseDouble(rng, rimCurlMin, rimCurlMax);             rng = mixSeed(rng, 23L)
    val domeFlatten  = HashUtil.chooseDouble(rng, domeFlattenMin, domeFlattenMax)

    val stemTopY = p.worldY + stemHeight - 1//todo maybe remove -1
    // Underside reference — the flat bottom of the cap center
    val undersideY   = stemTopY
    val rimDropBlocks = (capRadius * rimDrop)

    val sectorMultipliers = DoubleArray(capSectorCount) { sector ->
      HashUtil.chooseDouble(mixSeed(p.seed, sector.toLong() * 97L), 1.0 - capSectorStrength, 1.0 + capSectorStrength)
    }

    // --- Stem ---
    var centerX = p.worldX.toDouble()
    var centerZ = p.worldZ.toDouble()
    val iStemRadius = stemRadius.toInt() + 1
    val stemNoiseSource = region.ctx.noise.get(stemNoise)
    //val capBottomY = undersideY - rimDropBlocks.toInt() - 1

    for (dy in 0 until stemHeight) {
      val worldY = p.worldY + dy
      if (worldY >= undersideY) break

      val wanderSeed = mixSeed(p.seed, dy.toLong())
      centerX += HashUtil.chooseDouble(wanderSeed, -stemWanderStrength.toDouble(), stemWanderStrength.toDouble())
      centerZ += HashUtil.chooseDouble(mixSeed(wanderSeed, 5L), -stemWanderStrength.toDouble(), stemWanderStrength.toDouble())

      val stemProgress = dy.toDouble() / stemHeight
      val effectiveRadius = (stemRadius * Curve.lerp(1.0, 0.65, stemProgress)).toFloat()
      val r2 = effectiveRadius * effectiveRadius

      for (ix in -iStemRadius..iStemRadius) {
        for (iz in -iStemRadius..iStemRadius) {
          val bx = (centerX + ix).toInt()
          val bz = (centerZ + iz).toInt()

          if (!region.isInRegion(bx, worldY, bz)) continue
          if (!region.terrainQueries.isEmpty(bx, worldY, bz)) continue

          val lx = bx - centerX
          val lz = bz - centerZ
          val noiseVal = stemNoiseSource.noise3D(bx, worldY, bz)
          if (lx * lx + lz * lz <= r2 * (1.0 + stemNoiseStrength * noiseVal)) {
            if(dy == 0){
              GenUtil.placeTillGround(region, region.ctx.random, bx, worldY, bz, stemBlock)
              continue
            }

            val block = stemBlock.pickBlock(region, region.ctx.random, bx, worldY, bz) ?: continue
            region.setBlock(bx, worldY, bz, block)
          }
        }
      }
    }

    // --- Cap ---
    val cx = centerX.toInt()
    val cz = centerZ.toInt()
    val iCapRadius = capRadius.toInt() + 1
    val scanMin = (undersideY - rimDropBlocks - capThickness - 1).toInt()
    val scanMax = (undersideY + capDomeHeight + capThickness + 1).toInt()

    for (ix in -iCapRadius..iCapRadius) {
      for (iz in -iCapRadius..iCapRadius) {
        val bx = cx + ix
        val bz = cz + iz

        val lx = ix.toDouble()
        val lz = iz.toDouble()
        val horizDist = Math.sqrt(lx * lx + lz * lz)

        // Sector radius multiplier
        val angle = Math.atan2(lz, lx)
        val sector = (((angle / (2 * Math.PI) + 0.5) * capSectorCount).toInt()
          .coerceIn(0, capSectorCount - 1))
        val effectiveCapRadius = capRadius * sectorMultipliers[sector]

        val t = horizDist / effectiveCapRadius  // 0 = center, 1 = outer edge
        if (t > 1.0) continue

        val colSeed = mixSeed(p.seed, mixSeed(bx.toLong() * 31L, bz.toLong() * 97L))

        // Edge erosion
        if (t > 0.8) {
          val erosionProgress = (t - 0.8) / 0.2
          if (!chance(mixSeed(colSeed, 41L), 1.0 - erosionProgress * capEdgeErosionChance * 10.0)) continue
        }

        // Per-column height jitter
        val jitter = HashUtil.chooseDouble(
          mixSeed(colSeed, 53L),
          -capDomeHeight * capColumnHeightJitter,
          capDomeHeight * capColumnHeightJitter
        )

        // ---- Profile curve ----
        // Dome component: cosine arch from center (full height) to edge (zero)
        // domeFlatten blends toward a softer raised cosine so the top isn't too peaked
        val cosineArch  = Math.cos(t * Math.PI * 0.5)            // 1→0 smoothly
        val flattenedArch = Curve.lerp(cosineArch, cosineArch * cosineArch, domeFlatten)
        val domeY = capDomeHeight * flattenedArch

        // Rim droop component: hangs below underside, only kicks in near the edge
        // Using smoothstep so the droop starts gently rather than from t=0
        val droopStart = 0.45
        val droopT = Curve.smoothstep(droopStart, 1.0, t)
        val droopY = rimDropBlocks * Math.pow(droopT, rimCurl)

        // Final surface Y: dome lifts up, droop pulls edge down
        val surfaceY = undersideY + domeY - droopY + jitter

        //
        val steepness = Curve.smoothstep(0.5, 1.0, t)
        val gapPad = (steepness * (rimDropBlocks * 0.4 + capThickness + 1)).toInt()

        val bottomOfCap = minOf(
          (undersideY - droopY + jitter * 0.3).toInt() - gapPad,
          surfaceY.toInt() - capThickness
        )

        for (worldY in scanMin..scanMax) {
          if (!region.isInRegion(bx, worldY, bz)) continue
          if (!region.terrainQueries.isEmpty(bx, worldY, bz)) continue

          if (worldY in bottomOfCap..surfaceY.toInt()) {
            val block = capBlock.pickBlock(region, region.ctx.random, bx, worldY, bz) ?: continue
            region.setBlock(bx, worldY, bz, block)
          }
        }
        //

        // Place a shell of `capThickness` blocks below the surface
        /*for (worldY in scanMin..scanMax) {
          if (!region.isInRegion(bx, worldY, bz)) continue
          if (!region.terrainQueries.isEmpty(bx, worldY, bz)) continue

          val distToSurface = surfaceY - worldY

          // Block is inside if it's at or just below the surface
          if (distToSurface >= 0.0 && distToSurface < capThickness) {
            val block = capBlock.getBlock(region, region.ctx.random, bx, worldY, bz) ?: continue
            region.setBlock(bx, worldY, bz, block)
          }
        }*/
      }
    }
  }
}
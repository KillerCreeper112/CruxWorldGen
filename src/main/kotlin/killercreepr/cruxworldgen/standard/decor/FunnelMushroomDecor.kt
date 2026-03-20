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

open class FunnelMushroomDecor(
  chancePerPoint: Double = 0.04,

  stemHeightMin: Int = 4,
  stemHeightMax: Int = 10,
  stemRadiusMin: Float = 0.8f,
  stemRadiusMax: Float = 1.8f,
  stemWanderStrength: Float = 0.12f,

  /** Outer radius at the top rim of the funnel */
  capRadiusMin: Float = 4f,
  capRadiusMax: Float = 8f,
  /** Height of the funnel wall */
  val capHeightMin: Int = 4,
  val capHeightMax: Int = 9,
  /** Wall thickness as a fraction of outer radius — lower = thinner walls */
  val wallThicknessFractionMin: Double = 0.18,
  val wallThicknessFractionMax: Double = 0.32,
  /** How curved the outer wall is:
   *  < 1.0 = flares outward (trumpet shape)
   *  = 1.0 = straight cone
   *  > 1.0 = pinches inward toward rim (goblet shape) */
  val outerCurveMin: Double = 0.7,
  val outerCurveMax: Double = 1.2,
  /** Tilt of the funnel off vertical — gives a lopsided organic lean */
  val tiltStrengthMin: Float = 0.0f,
  val tiltStrengthMax: Float = 1.2f,

  /** Rim irregularity — random height variation around the top edge */
  val rimHeightJitterMin: Double = 0.2,
  val rimHeightJitterMax: Double = 0.5,
  val capSectorCount: Int = 9,
  val capSectorStrength: Double = 0.15,
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

    val stemHeight     = HashUtil.chooseInt(rng, stemHeightMin, stemHeightMax);                rng = mixSeed(rng, 3L)
    val stemRadius     = HashUtil.chooseFloat(rng, stemRadiusMin, stemRadiusMax);              rng = mixSeed(rng, 7L)
    val capRadius      = HashUtil.chooseFloat(rng, capRadiusMin, capRadiusMax);                rng = mixSeed(rng, 11L)
    val capHeight      = HashUtil.chooseInt(rng, capHeightMin, capHeightMax);                  rng = mixSeed(rng, 13L)
    val wallThickness  = HashUtil.chooseDouble(rng, wallThicknessFractionMin, wallThicknessFractionMax); rng = mixSeed(rng, 17L)
    val outerCurve     = HashUtil.chooseDouble(rng, outerCurveMin, outerCurveMax);             rng = mixSeed(rng, 19L)
    val tiltStrength   = HashUtil.chooseFloat(rng, tiltStrengthMin, tiltStrengthMax);          rng = mixSeed(rng, 23L)
    val tiltAngle      = HashUtil.chooseDouble(rng, 0.0, 2 * Math.PI);                        rng = mixSeed(rng, 29L)
    val rimHeightJitter = HashUtil.chooseDouble(rng, rimHeightJitterMin, rimHeightJitterMax)

    // Tilt direction vector — the funnel leans this way
    val tiltDx = Math.cos(tiltAngle) * tiltStrength
    val tiltDz = Math.sin(tiltAngle) * tiltStrength

    val stemTopY  = p.worldY + stemHeight
    val capBaseY  = stemTopY  // funnel wall starts at stem top
    val capTopY   = capBaseY + capHeight

    // Sector multipliers for rim irregularity
    val sectorMultipliers = DoubleArray(capSectorCount) { sector ->
      HashUtil.chooseDouble(mixSeed(p.seed, sector.toLong() * 97L), 1.0 - capSectorStrength, 1.0 + capSectorStrength)
    }

    // --- Stem ---
    var centerX = p.worldX.toDouble()
    var centerZ = p.worldZ.toDouble()
    val iStemRadius = stemRadius.toInt() + 1
    val stemNoiseSource = region.ctx.noise.get(stemNoise)

    for (dy in 0 until stemHeight) {
      val worldY = p.worldY + dy

      val wanderSeed = mixSeed(p.seed, dy.toLong())
      centerX += HashUtil.chooseDouble(wanderSeed, -stemWanderStrength.toDouble(), stemWanderStrength.toDouble())
      centerZ += HashUtil.chooseDouble(mixSeed(wanderSeed, 5L), -stemWanderStrength.toDouble(), stemWanderStrength.toDouble())

      val stemProgress = dy.toDouble() / stemHeight
      val effectiveRadius = (stemRadius * Curve.lerp(1.0, 0.75, stemProgress)).toFloat()
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

    // --- Funnel Cap ---
    val cx = centerX.toInt()
    val cz = centerZ.toInt()
    val iCapRadius = capRadius.toInt() + 2

    for (ix in -iCapRadius..iCapRadius) {
      for (iz in -iCapRadius..iCapRadius) {
        val bx = cx + ix
        val bz = cz + iz

        val lx = ix.toDouble()
        val lz = iz.toDouble()
        val horizDist = Math.sqrt(lx * lx + lz * lz)

        // Sector radius multiplier for rim shape
        val angle = Math.atan2(lz, lx)
        val sector = (((angle / (2 * Math.PI) + 0.5) * capSectorCount).toInt()
          .coerceIn(0, capSectorCount - 1))
        val sectorMul = sectorMultipliers[sector]
        val effectiveCapRadius = capRadius * sectorMul

        val normalizedDist = horizDist / effectiveCapRadius
        if (normalizedDist > 1.0) continue

        // Edge erosion on outer rim
        val colSeed = mixSeed(p.seed, mixSeed(bx.toLong() * 31L, bz.toLong() * 97L))
        if (normalizedDist > 0.8) {
          val erosionProgress = (normalizedDist - 0.8) / 0.2
          if (!chance(mixSeed(colSeed, 41L), 1.0 - erosionProgress * capEdgeErosionChance * 10.0)) continue
        }

        // Per-column rim height jitter — makes the top edge ragged
        val colRimJitter = HashUtil.chooseDouble(
          mixSeed(colSeed, 53L), -rimHeightJitter * capHeight, rimHeightJitter * capHeight
        )

        for (worldY in capBaseY..capTopY + capHeight) {
          if (!region.isInRegion(bx, worldY, bz)) continue
          if (!region.terrainQueries.isEmpty(bx, worldY, bz)) continue

          // Height progress from base (0) to top (1) of the funnel wall
          val heightProgress = (worldY - capBaseY).toDouble() / capHeight

          if (heightProgress < 0.0) continue

          // Tilt offset — the funnel axis leans, shifting the center at each height
          val tiltOffsetX = tiltDx * heightProgress * capHeight
          val tiltOffsetZ = tiltDz * heightProgress * capHeight
          val tiltedLx = lx - tiltOffsetX
          val tiltedLz = lz - tiltOffsetZ
          val tiltedDist = Math.sqrt(tiltedLx * tiltedLx + tiltedLz * tiltedLz)

          // Outer wall: radius grows from stemRadius at base to capRadius at top
          // outerCurve controls the profile of the flare
          val outerRadius = stemRadius + (effectiveCapRadius - stemRadius) *
            Math.pow(heightProgress, outerCurve)

          // Inner hollow: radius grows similarly but offset by wall thickness
          // At the very base the wall closes completely into the stem
          val innerRadius = (outerRadius - outerRadius * wallThickness)
            .coerceAtLeast(0.0)

          // Rim cutoff with per-column jitter — blocks above the jittered rim are skipped
          val rimCutoff = capTopY + colRimJitter
          if (worldY > rimCutoff) continue

          // Block is inside the wall if it's within outer radius but outside inner radius
          val inWall = tiltedDist <= outerRadius && tiltedDist >= innerRadius

          // Close the base of the funnel — fill solid at the bottom few layers
          // so it connects cleanly to the stem
          val baseFill = heightProgress < (stemRadius / capRadius) * 1.5
          val inBase = baseFill && tiltedDist <= outerRadius

          if (inWall || inBase) {
            val block = capBlock.pickBlock(region, region.ctx.random, bx, worldY, bz) ?: continue
            region.setBlock(bx, worldY, bz, block)
          }
        }
      }
    }
  }
}
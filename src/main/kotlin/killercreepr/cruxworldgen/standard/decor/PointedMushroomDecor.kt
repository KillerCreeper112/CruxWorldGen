package killercreepr.cruxworldgen.standard.decor

import killercreepr.crux.core.util.CruxMath
import killercreepr.cruxworldgen.api.block.BlockGetter
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.decor.DecorationPass
import killercreepr.cruxworldgen.api.decor.Placement
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.noise.NoiseKey
import killercreepr.cruxworldgen.api.util.Curve
import killercreepr.cruxworldgen.api.util.HashUtil
import killercreepr.cruxworldgen.api.util.HashUtil.chance
import killercreepr.cruxworldgen.api.util.HashUtil.mixSeed

open class PointedMushroomDecor(
  chancePerPoint: Double = 0.04,

  stemHeightMin: Int = 8,
  stemHeightMax: Int = 18,
  stemRadiusMin: Float = 1.5f,
  stemRadiusMax: Float = 3.0f,
  stemWanderStrength: Float = 0.18f,

  capRadiusMin: Float = 6f,
  capRadiusMax: Float = 12f,

  /** Height of the pointed top above the underside — higher = taller spike */
  val capPointHeightMin: Float = 4f,
  val capPointHeightMax: Float = 9f,
  /** 1.0 = straight cone, 2.0 = paraboloid (rounder base, sharper tip), 0.5 = wider base very sharp tip */
  val capPointCurveMin: Double = 0.8,
  val capPointCurveMax: Double = 1.6,

  /** How far the underside sits below the stem top */
  val capUndersideOffsetMin: Int = 2,
  val capUndersideOffsetMax: Int = 4,
  /** How sharply the rim curls downward */
  val rimCurlMin: Double = 1.5,
  val rimCurlMax: Double = 3.0,
  /** How far the rim hangs below the underside as a fraction of capRadius */
  val rimDropFractionMin: Double = 0.25,
  val rimDropFractionMax: Double = 0.45,

  val capSectorCount: Int = 7,
  val capSectorStrength: Double = 0.18,
  val capColumnHeightJitter: Double = 0.0,
  val capEdgeErosionChance: Double = 0.0,

  stemNoiseStrength: Double = 0.9,

  stemNoise: NoiseKey,
  capNoise: NoiseKey,
  stemBlock: BlockGetter,
  capBlock: BlockGetter,
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

    val stemHeight       = HashUtil.chooseInt(rng, stemHeightMin, stemHeightMax);              rng = mixSeed(rng, 3L)
    val stemRadius       = HashUtil.chooseFloat(rng, stemRadiusMin, stemRadiusMax);            rng = mixSeed(rng, 7L)
    val capRadius        = HashUtil.chooseFloat(rng, capRadiusMin, capRadiusMax);              rng = mixSeed(rng, 11L)
    val capPointHeight   = HashUtil.chooseFloat(rng, capPointHeightMin, capPointHeightMax);    rng = mixSeed(rng, 13L)
    val capPointCurve    = HashUtil.chooseDouble(rng, capPointCurveMin, capPointCurveMax);     rng = mixSeed(rng, 17L)
    val undersideOffset  = HashUtil.chooseInt(rng, capUndersideOffsetMin, capUndersideOffsetMax); rng = mixSeed(rng, 19L)
    val rimCurl          = HashUtil.chooseDouble(rng, rimCurlMin, rimCurlMax);                 rng = mixSeed(rng, 23L)
    val rimDrop          = HashUtil.chooseDouble(rng, rimDropFractionMin, rimDropFractionMax)

    val stemTopY      = p.worldY + stemHeight
    val undersideY    = stemTopY - undersideOffset
    val rimDropBlocks = (capRadius * rimDrop).toInt()

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
            val block = stemBlock.getBlock(region, region.ctx.random, bx, worldY, bz) ?: continue
            region.setBlock(bx, worldY, bz, block)
          }
        }
      }
    }

    // --- Cap ---
    val cx = centerX.toInt()
    val cz = centerZ.toInt()
    val iCapRadius = capRadius.toInt() + 1
    val capScanMin = undersideY - rimDropBlocks - 1
    val capScanMax = (undersideY + capPointHeight).toInt() + 1

    for (ix in -iCapRadius..iCapRadius) {
      for (iz in -iCapRadius..iCapRadius) {
        val bx = cx + ix
        val bz = cz + iz

        val lx = ix.toDouble()
        val lz = iz.toDouble()
        val horizDist = Math.sqrt(lx * lx + lz * lz)

        val angle = Math.atan2(lz, lx)
        val sector = (((angle / (2 * Math.PI) + 0.5) * capSectorCount).toInt()
          .coerceIn(0, capSectorCount - 1))
        val effectiveCapRadius = capRadius * sectorMultipliers[sector]

        val normalizedDist = horizDist / effectiveCapRadius
        if (normalizedDist > 1.0) continue

        val colSeed = mixSeed(p.seed, mixSeed(bx.toLong() * 31L, bz.toLong() * 97L))
        val heightJitter = HashUtil.chooseDouble(
          colSeed, -capPointHeight * capColumnHeightJitter, capPointHeight * capColumnHeightJitter
        )

        // Edge erosion on rim
        if (normalizedDist > 0.75) {
          val erosionProgress = (normalizedDist - 0.75) / 0.25
          if (!chance(mixSeed(colSeed, 41L), 1.0 - erosionProgress * capEdgeErosionChance * 10.0)) continue
        }

        for (worldY in capScanMin..capScanMax) {
          if (!region.isInRegion(bx, worldY, bz)) continue
          if (!region.terrainQueries.isEmpty(bx, worldY, bz)) continue

          val dy = worldY - undersideY

          val inCone = run {
            if (dy < 0) return@run false
            // Radius at this height shrinks from capRadius at dy=0 to 0 at dy=capPointHeight
            // capPointCurve controls the profile:
            //   < 1.0 = concave sides (narrow waist, flared base)
            //   = 1.0 = straight cone
            //   > 1.0 = convex sides (bulges near base, sharp tip)
            val heightProgress = (dy - heightJitter) / capPointHeight
            if (heightProgress < 0.0 || heightProgress > 1.0) return@run false
            val radiusAtHeight = effectiveCapRadius * Math.pow(1.0 - heightProgress, capPointCurve)
            horizDist <= radiusAtHeight
          }

          val inRim = run {
            if (dy > 0) return@run false
            val rimProgress = normalizedDist
            val hangDepth = -(rimDropBlocks * Math.pow(rimProgress, rimCurl))
            dy >= hangDepth + heightJitter * 0.3
          }

          if (inCone || inRim) {
            val block = capBlock.getBlock(region, region.ctx.random, bx, worldY, bz) ?: continue
            region.setBlock(bx, worldY, bz, block)
          }
        }
      }
    }
  }
}
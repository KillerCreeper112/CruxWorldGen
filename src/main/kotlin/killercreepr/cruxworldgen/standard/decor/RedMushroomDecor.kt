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

open class RedMushroomDecor(
  chancePerPoint: Double = 0.04,

  stemHeightMin: Int = 8,
  stemHeightMax: Int = 18,
  stemRadiusMin: Float = 1.5f,
  stemRadiusMax: Float = 3.0f,
  stemWanderStrength: Float = 0.18f,

  capRadiusMin: Float = 6f,
  capRadiusMax: Float = 12f,

  /** Height of the dome above the flat underside — lower = flatter top */
  val capDomeScaleMin: Float = 0.35f,
  val capDomeScaleMax: Float = 0.55f,
  /** How far the underside of the cap sits below the stem top */
  val capUndersideOffsetMin: Int = 2,
  val capUndersideOffsetMax: Int = 4,
  /** How sharply the rim curls downward — higher = more pronounced umbrella droop */
  val rimCurlMin: Double = 1.5,
  val rimCurlMax: Double = 3.0,
  /** How far the rim extends below the underside as a fraction of capRadius */
  val rimDropFractionMin: Double = 0.3,
  val rimDropFractionMax: Double = 0.5,

  val capSectorCount: Int = 7,
  val capSectorStrength: Double = 0.18,
  val capColumnHeightJitter: Double = 0.0,
  val capEdgeErosionChance: Double = 0.0,

  stemNoiseStrength: Double = 0.9,
  capNoiseStrength: Double = 0.9,

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
  // These aren't used — cap placement is fully overridden
  capHeightScaleMin = 0.3f,
  capHeightScaleMax = 0.3f,
  capOverhangMin = 0f,
  capOverhangMax = 0f,
  stemNoiseStrength = stemNoiseStrength,
  capNoiseStrength = capNoiseStrength,
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
    var rng = HashUtil.mixSeed(p.seed, chanceSalt)

    val stemHeight  = HashUtil.chooseInt(rng, stemHeightMin, stemHeightMax);        rng = HashUtil.mixSeed(rng, 3L)
    val stemRadius  = HashUtil.chooseFloat(rng, stemRadiusMin, stemRadiusMax);      rng = HashUtil.mixSeed(rng, 7L)
    val capRadius   = HashUtil.chooseFloat(rng, capRadiusMin, capRadiusMax);        rng = HashUtil.mixSeed(rng, 11L)
    val capDomeScale = HashUtil.chooseFloat(rng, capDomeScaleMin, capDomeScaleMax); rng = HashUtil.mixSeed(rng, 13L)
    val undersideOffset = HashUtil.chooseInt(rng, capUndersideOffsetMin, capUndersideOffsetMax); rng =
      HashUtil.mixSeed(rng, 17L)
    val rimCurl     = HashUtil.chooseDouble(rng, rimCurlMin, rimCurlMax);           rng = HashUtil.mixSeed(rng, 19L)
    val rimDrop     = HashUtil.chooseDouble(rng, rimDropFractionMin, rimDropFractionMax)

    val stemTopY      = p.worldY + stemHeight
    // Underside sits a few blocks below stem top, dome rises above it
    val undersideY    = stemTopY - undersideOffset
    val domeHeight    = capRadius * capDomeScale
    val rimDropBlocks = (capRadius * rimDrop).toInt()

    // Sector radius multipliers — makes perimeter irregular
    val sectorMultipliers = DoubleArray(capSectorCount) { sector ->
      HashUtil.chooseDouble(HashUtil.mixSeed(p.seed, sector.toLong() * 97L), 1.0 - capSectorStrength, 1.0 + capSectorStrength)
    }

    // --- Stem (reuse parent logic) ---
    var centerX = p.worldX.toDouble()
    var centerZ = p.worldZ.toDouble()
    val iStemRadius = stemRadius.toInt() + 1
    val stemNoiseSource = region.ctx.noise.get(stemNoise)

    for (dy in 0 until stemHeight) {
      val worldY = p.worldY + dy
      if (worldY >= undersideY) break

      val wanderSeed = HashUtil.mixSeed(p.seed, dy.toLong())
      centerX += HashUtil.chooseDouble(wanderSeed, -stemWanderStrength.toDouble(), stemWanderStrength.toDouble())
      centerZ += HashUtil.chooseDouble(HashUtil.mixSeed(wanderSeed, 5L), -stemWanderStrength.toDouble(), stemWanderStrength.toDouble())

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
    val capScanMin = undersideY - rimDropBlocks - 1
    val capScanMax = (undersideY + domeHeight).toInt() + 1

    for (ix in -iCapRadius..iCapRadius) {
      for (iz in -iCapRadius..iCapRadius) {
        val bx = cx + ix
        val bz = cz + iz

        val lx = ix.toDouble()
        val lz = iz.toDouble()
        val horizDist = Math.sqrt(lx * lx + lz * lz)

        // Angular sector multiplier
        val angle = Math.atan2(lz, lx)
        val sector = (((angle / (2 * Math.PI) + 0.5) * capSectorCount).toInt()
          .coerceIn(0, capSectorCount - 1))
        val effectiveCapRadius = capRadius * sectorMultipliers[sector]

        val normalizedDist = horizDist / effectiveCapRadius
        if (normalizedDist > 1.0) continue

        // Per-column jitter
        val colSeed = HashUtil.mixSeed(p.seed, HashUtil.mixSeed(bx.toLong() * 31L, bz.toLong() * 97L))
        val heightJitter = HashUtil.chooseDouble(colSeed, -domeHeight * capColumnHeightJitter, domeHeight * capColumnHeightJitter)

        // Edge erosion on the rim
        if (normalizedDist > 0.75) {
          val erosionProgress = (normalizedDist - 0.75) / 0.25
          if (!HashUtil.chance(HashUtil.mixSeed(colSeed, 41L), 1.0 - erosionProgress * capEdgeErosionChance * 10.0)) continue
        }

        for (worldY in capScanMin..capScanMax) {
          if (!region.isInRegion(bx, worldY, bz)) continue
          if (!region.terrainQueries.isEmpty(bx, worldY, bz)) continue

          val dy = worldY - undersideY  // negative = below underside, positive = above

          val inDome = run {
            // Oblate dome above the underside: (r/capRadius)² + (dy/domeHeight)² <= 1, dy >= 0
            if (dy < 0) return@run false
            val domeResult = normalizedDist * normalizedDist +
              ((dy - heightJitter) * (dy - heightJitter)) / (domeHeight * domeHeight)
            domeResult <= 1.0
          }

          val inRim = run {
            // Rim curl: droops below the underside, curling inward toward center
            // At normalizedDist=0 the rim doesn't exist; at normalizedDist=1 it hangs furthest down
            if (dy > 0) return@run false
            val rimProgress = normalizedDist  // 0 at center, 1 at edge
            // How far down this rim position hangs — power curve creates the curl
            val hangDepth = -(rimDropBlocks * Math.pow(rimProgress, rimCurl))
            dy >= hangDepth + heightJitter * 0.5
          }

          if (inDome || inRim) {
            val block = capBlock.pickBlock(region, region.ctx.random, bx, worldY, bz) ?: continue
            region.setBlock(bx, worldY, bz, block)
          }
        }
      }
    }
  }
}
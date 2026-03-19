package killercreepr.cruxworldgen.standard.decor

import killercreepr.crux.core.util.CruxMath
import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.block.BlockGetter
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.decor.Decoration
import killercreepr.cruxworldgen.api.decor.DecorationPass
import killercreepr.cruxworldgen.api.decor.Placement
import killercreepr.cruxworldgen.api.decor.PropPoint
import killercreepr.cruxworldgen.api.decor.VolumetricDecoration
import killercreepr.cruxworldgen.api.decor.VolumetricPropPoint
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.noise.NoiseKey
import killercreepr.cruxworldgen.api.util.HashUtil
import killercreepr.cruxworldgen.api.util.HashUtil.chance
import killercreepr.cruxworldgen.api.util.HashUtil.mixSeed

open class BrownMushroomDecor(
  val chancePerPoint: Double = 0.04,

  val stemHeightMin: Int = 8,
  val stemHeightMax: Int = 18,

  val stemRadiusMin: Float = 1.5f,
  val stemRadiusMax: Float = 3.0f,
  /** How much the stem can wander horizontally per block */
  val stemWanderStrength: Float = 0.18f,

  val capRadiusMin: Float = 5f,
  val capRadiusMax: Float = 10f,
  /** Vertical squash of the cap — lower = flatter */
  val capHeightScaleMin: Float = 0.18f,
  val capHeightScaleMax: Float = 0.30f,
  /** How far the cap hangs below the top of the stem */
  val capOverhangMin: Float = 0.0f,
  val capOverhangMax: Float = 0.0f,

  val stemNoiseStrength: Double = .9,
  val capNoiseStrength: Double = .9,

  val stemNoise: NoiseKey,
  val capNoise: NoiseKey,

  val stemBlock: BlockGetter,
  val capBlock: BlockGetter,

  val chanceSalt: Long = CruxMath.random().nextLong(),
  override val pass: DecorationPass = DecorationPass.SURFACE
) : Decoration {

  /** Controls distribution. Examples: grid spacing, noise chance, biome-weight scaling */
  override fun shouldTry(
    region: LimitedRegion,
    point: PropPoint,
    biomeBlend: BiomeBlendSample
  ): Boolean {
    val s = mixSeed(region.ctx.worldContext.seed, point.worldX, point.worldZ, chanceSalt)
    return chance(s, chancePerPoint)
  }

  /** Pattern scan: find an anchor/placement candidate */
  override fun findPlacement(
    region: LimitedRegion,
    point: PropPoint,
    biomeBlend: BiomeBlendSample
  ): Placement? {
    val x = point.worldX
    val z = point.worldZ
    val queries = region.terrainQueries

    val y = region.terrainSnapshot.terrain2D.surfaceY(x, z)

    if (!region.isInRegion(x, y, z)) return null
    if(!region.isInRegion(x, y+1, z)) return null

    if (queries.isSolid(x, y, z) && queries.isEmpty(x, y + 1, z)) {
      return Placed(x, y + 1, z, point.seed)
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

    val stemHeight = HashUtil.chooseInt(rng, stemHeightMin, stemHeightMax)
    rng = mixSeed(rng, 3L)
    val stemRadius = HashUtil.chooseFloat(rng, stemRadiusMin, stemRadiusMax)
    rng = mixSeed(rng, 7L)
    val capRadius = HashUtil.chooseFloat(rng, capRadiusMin, capRadiusMax)
    rng = mixSeed(rng, 11L)
    val capHeightScale = HashUtil.chooseFloat(rng, capHeightScaleMin, capHeightScaleMax)
    rng = mixSeed(rng, 13L)
    val capOverhang = HashUtil.chooseFloat(rng, capOverhangMin, capOverhangMax)

    val stemNoiseSource = region.ctx.noise.get(stemNoise)
    val capNoiseSource = region.ctx.noise.get(capNoise)

    // --- Stem ---
    // Track center wander so the stem curves naturally
    var centerX = p.worldX.toDouble()
    var centerZ = p.worldZ.toDouble()

    val iStemRadius = stemRadius.toInt() + 1
    val stemTopY = p.worldY + stemHeight

    for (dy in 0 until stemHeight) {
      val worldY = p.worldY + dy

      // Slight random wander of center per layer
      val wanderSeed = mixSeed(p.seed, dy.toLong())
      centerX += HashUtil.chooseDouble(wanderSeed, -stemWanderStrength.toDouble(), stemWanderStrength.toDouble())
      centerZ += HashUtil.chooseDouble(mixSeed(wanderSeed, 5L), -stemWanderStrength.toDouble(), stemWanderStrength.toDouble())

      for (ix in -iStemRadius..iStemRadius) {
        for (iz in -iStemRadius..iStemRadius) {
          val bx = (centerX + ix).toInt()
          val bz = (centerZ + iz).toInt()

          if (!region.isInRegion(bx, worldY, bz)) continue
          if (!region.terrainQueries.isEmpty(bx, worldY, bz)) continue

          val lx = bx - centerX
          val lz = bz - centerZ
          val dist2 = lx * lx + lz * lz
          val r2 = stemRadius * stemRadius

          val noiseVal = stemNoiseSource.noise3D(bx, worldY, bz)
          if (dist2 <= r2 * (1.0 + stemNoiseStrength * noiseVal)) {
            val block = stemBlock.getBlock(region, region.ctx.random, bx, worldY, bz) ?: continue
            region.setBlock(bx, worldY, bz, block)
          }
        }
      }
    }

    // --- Cap ---
    // Flat oblate spheroid: (dx/capRadius)² + (dz/capRadius)² + (dy/(capRadius*capHeightScale))² <= 1
    val capCenterY = stemTopY - capOverhang.toInt()
    val iCapRadius = capRadius.toInt() + 1
    val capVertRadius = capRadius * capHeightScale

    val cx = centerX.toInt()
    val cz = centerZ.toInt()

    for (ix in -iCapRadius..iCapRadius) {
      for (iz in -iCapRadius..iCapRadius) {
        for (iy in -iCapRadius..iCapRadius) {
          val bx = cx + ix
          val worldY = capCenterY + iy
          val bz = cz + iz

          if (!region.isInRegion(bx, worldY, bz)) continue
          if (!region.terrainQueries.isEmpty(bx, worldY, bz)) continue

          val lx = ix.toDouble()
          val ly = iy.toDouble()
          val lz = iz.toDouble()

          val equationResult =
            (lx * lx) / (capRadius * capRadius) +
            (lz * lz) / (capRadius * capRadius) +
            (ly * ly) / (capVertRadius * capVertRadius)

          val noiseVal = capNoiseSource.noise3D(bx, worldY, bz)
          if (equationResult <= 1.0 + capNoiseStrength * noiseVal) {
            val block = capBlock.getBlock(region, region.ctx.random, bx, worldY, bz) ?: continue
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
    val seed: Long
  ) : Placement
}
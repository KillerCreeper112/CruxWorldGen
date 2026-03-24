package killercreepr.cruxworldgen.standard.decor.volumetric

import killercreepr.crux.core.util.CruxMath
import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.decor.DecorationPass
import killercreepr.cruxworldgen.api.decor.Placement
import killercreepr.cruxworldgen.api.decor.VolumetricDecoration
import killercreepr.cruxworldgen.api.decor.VolumetricPropPoint
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.util.HashUtil
import killercreepr.cruxworldgen.api.util.HashUtil.chance
import killercreepr.cruxworldgen.api.util.HashUtil.mixSeed
import killercreepr.cruxworldgen.core.decor.SimpleVolumetricPropPoint

open class Group3DDecor(
  val decorations: List<VolumetricDecoration>,
  val minRadius: Int = 2,
  val maxRadius: Int = 5,

  val minYRadius: Int = 0,
  val maxYRadius: Int = 0,

  val minPickAmount: Int = 3,
  val maxPickAmount: Int = 7,

  val applyChildChance: Boolean = false,
  val biomeCheck: Boolean = true,

  val chancePerPoint: Double = 0.2,
  val chanceSalt: Long = CruxMath.random().nextLong(),
  val pickSalt: Long = CruxMath.random().nextLong(),
  val coordinateSalt: Long = CruxMath.random().nextLong(),
  override val pass: DecorationPass = DecorationPass.SURFACE
) : VolumetricDecoration {
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
    val pickAmount = HashUtil.chooseInt(
      seed = mixSeed(
        seed = region.ctx.worldContext.seed,
        x = point.worldX, y = point.worldY, z = point.worldZ,
        salt = pickSalt
      ),
      minPickAmount, maxPickAmount
    )
    if(pickAmount < 1) return null
    return findPlacement(region, point, biomeBlend, biome, pickAmount)
  }

  open fun findPlacement(
    region: LimitedRegion,
    point: VolumetricPropPoint,
    biomeBlend: BiomeBlendSample,
    biome: Biome,
    pickAmount: Int
  ): Placement?{
    return Placed(
      seed = point.seed,
      x = point.worldX,
      y = point.worldY,
      z = point.worldZ,
      pickAmount = pickAmount,
    )
  }

  /** Apply: place blocks using placement info */
  override fun place(
    region: LimitedRegion,
    p: Placement,
    biomeBlend: BiomeBlendSample,
    biome: Biome
  ) {
    p as Placed
    if (decorations.isEmpty()) return

    val baseBiome = region.getBiome(p.x, p.y, p.z)

    val chosen = LinkedHashSet<Triple<Int, Int, Int>>()

    val minRadiusSq = minRadius * minRadius
    val maxRadiusSq = maxRadius * maxRadius
    val baseSeed = mixSeed(p.seed, p.x, p.y, p.z, coordinateSalt)

    for (i in 0 until p.pickAmount) {
      for (attempt in 0 until 16) {
        val seed = mixSeed(baseSeed, i, attempt, coordinateSalt)

        val dx = HashUtil.chooseInt(seed xor 0x9E3779B97F4A715L, -maxRadius, maxRadius)
        val dy = HashUtil.chooseInt(seed xor 0xC2B2A3D27D4EB4FL, -maxYRadius, maxYRadius)
        val dz = HashUtil.chooseInt(seed xor 0x165667B19E3779F9L, -maxRadius, maxRadius)

        val horizontalDist2 = dx * dx + dz * dz
        if (horizontalDist2 !in minRadiusSq..maxRadiusSq) continue
        if (kotlin.math.abs(dy) !in minYRadius..maxYRadius) continue

        val x = p.x + dx
        val y = p.y + dy
        val z = p.z + dz
        if(!region.isInRegion(x, y,z)) continue
        val checkBiome = region.getBiome(x, y, z)
        if(biomeCheck && checkBiome != baseBiome) continue
        val triple = Triple(x, y, z)

        if (!chosen.add(triple)) continue
        break
      }
    }

    for ((x, y, z) in chosen) {
      val pointSeed = mixSeed(p.seed, x, y, z, coordinateSalt)

      val decorIndex = HashUtil.chooseInt(pointSeed, 0, decorations.lastIndex)
      val decor = decorations.getOrNull(decorIndex) ?: continue

      val point = SimpleVolumetricPropPoint(
        x,
        y,
        z,
        region.ctx.wrapLocalX(x),
        region.ctx.wrapLocalZ(z),
        pointSeed
      )

      if (applyChildChance && !decor.shouldTry(region, point, biomeBlend, biome)) continue

      val decorPlacement = decor.findPlacement(region, point, biomeBlend, biome) ?: continue
      decor.place(region, decorPlacement, biomeBlend, biome)
    }
  }
  data class Placed(
    val seed: Long,
    val x: Int,
    val y: Int,
    val z : Int,
    val pickAmount: Int
  ): Placement
}
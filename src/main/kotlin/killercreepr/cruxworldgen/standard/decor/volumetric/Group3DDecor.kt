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

  val ignoreChance: Boolean = true,

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
    val randomSeed = mixSeed(p.seed, p.x, p.y, p.z, coordinateSalt)

    val chosen = mutableSetOf<Triple<Int, Int, Int>>()
    for(i in 0..<p.pickAmount){

      for(x in 0..16){
        val x = p.x + HashUtil.chooseInt(randomSeed, minRadius, maxRadius)
        val y = p.y + HashUtil.chooseInt(randomSeed, minYRadius, maxYRadius)
        val z = p.z + HashUtil.chooseInt(randomSeed, minRadius, maxRadius)
        val pair = Triple(x, y, z)
        if(chosen.contains(pair)) continue
        chosen.add(pair)
        break
      }
    }

    for ((x, y, z) in chosen) {
      val decor = decorations.randomOrNull() ?: continue
      val point = SimpleVolumetricPropPoint(
        x, y, z, region.ctx.wrapLocalX(x), region.ctx.wrapLocalZ(z),
        mixSeed(p.seed, x, z, coordinateSalt)
      )
      if(!ignoreChance){
        if(!decor.shouldTry(region, point, biomeBlend, biome)) continue
      }

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
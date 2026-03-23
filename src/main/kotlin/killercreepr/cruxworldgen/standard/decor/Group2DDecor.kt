package killercreepr.cruxworldgen.standard.decor

import killercreepr.crux.core.Crux
import killercreepr.crux.core.util.CruxMath
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.decor.Decoration
import killercreepr.cruxworldgen.api.decor.DecorationPass
import killercreepr.cruxworldgen.api.decor.Placement
import killercreepr.cruxworldgen.api.decor.PropPoint
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.util.HashUtil
import killercreepr.cruxworldgen.api.util.HashUtil.chance
import killercreepr.cruxworldgen.api.util.HashUtil.mixSeed
import killercreepr.cruxworldgen.core.decor.SimplePropPoint

open class Group2DDecor(
  val decorations: List<Decoration>,
  val minRadius: Int = 2,
  val maxRadius: Int = 5,

  val minPickAmount: Int = 3,
  val maxPickAmount: Int = 7,

  val applyChildChance: Boolean = false,
  val biomeCheck: Boolean = true,

  val chancePerPoint: Double = 0.2,
  val chanceSalt: Long = CruxMath.random().nextLong(),
  val pickSalt: Long = CruxMath.random().nextLong(),
  val coordinateSalt: Long = CruxMath.random().nextLong(),
  override val pass: DecorationPass = DecorationPass.SURFACE
) : Decoration {
  /** Controls distribution. Examples: grid spacing, noise chance, biome-weight scaling */
  override fun shouldTry(
    region: LimitedRegion,
    point: PropPoint,
    biomeBlend: BiomeBlendSample
  ): Boolean {
    val s = mixSeed(
      seed = region.ctx.worldContext.seed,
      x = point.worldX, z = point.worldZ,
      salt = chanceSalt
    )
    return chance(s, chancePerPoint)
  }

  /** Pattern scan: find an anchor/placement candidate */
  override fun findPlacement(
    region: LimitedRegion,
    point: PropPoint,
    biomeBlend: BiomeBlendSample
  ): Placement? {
    val pickAmount = HashUtil.chooseInt(
      seed = HashUtil.mixSeed(
        seed = region.ctx.worldContext.seed,
        x = point.worldX, z = point.worldZ,
        salt = pickSalt
      ),
      minPickAmount, maxPickAmount
    )
    if(pickAmount < 1) return null
    return Placed(
      seed = point.seed,
      x = point.worldX,
      z = point.worldZ,
      pickAmount = pickAmount,
    )
  }

  /** Apply: place blocks using placement info */
  override fun place(
    region: LimitedRegion,
    p: Placement,
    biomeBlend: BiomeBlendSample
  ) {
    p as Placed

    val chosen = LinkedHashSet<Pair<Int, Int>>()

    for (i in 0 until p.pickAmount) {
      for (attempt in 0 until 16) {
        val seed = HashUtil.mixSeed(
          HashUtil.mixSeed(p.seed, p.x, p.z, coordinateSalt),
          i,
          attempt,
          coordinateSalt
        )

        val dx = HashUtil.chooseInt(seed xor 0x9E3779B97F4A7C5L, -maxRadius, maxRadius)
        val dz = HashUtil.chooseInt(seed xor 0xC2B2AED27D4EB4FL, -maxRadius, maxRadius)

        val dist2 = dx * dx + dz * dz
        if (dist2 < minRadius * minRadius || dist2 > maxRadius * maxRadius) continue

        val x = p.x + dx
        val z = p.z + dz
        if(!region.isInRegion(x, z)) continue

        val pair = x to z

        if (!chosen.add(pair)) continue
        break
      }
    }

    for ((x, z) in chosen) {
      val pointSeed = HashUtil.mixSeed(p.seed, x, z, coordinateSalt)

      val decorIndex = HashUtil.chooseInt(pointSeed, 0, decorations.lastIndex)
      val decor = decorations.getOrNull(decorIndex) ?: continue

      val point = SimplePropPoint(
        x,
        z,
        region.ctx.wrapLocalX(x),
        region.ctx.wrapLocalZ(z),
        pointSeed
      )

      if (applyChildChance && !decor.shouldTry(region, point, biomeBlend)) continue

      val decorPlacement = decor.findPlacement(region, point, biomeBlend) ?: continue
      decor.place(region, decorPlacement, biomeBlend)
    }
  }

  data class Placed(
    val seed: Long,
    val x: Int,
    val z : Int,
    val pickAmount: Int
  ): Placement
}
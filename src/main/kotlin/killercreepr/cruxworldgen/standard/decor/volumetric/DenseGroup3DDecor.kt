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
import killercreepr.cruxworldgen.standard.decor.volumetric.Group3DDecor.Placed
import kotlin.math.abs
import kotlin.math.sqrt

open class DenseGroup3DDecor(
  val decorations: List<VolumetricDecoration>,

  val minRadius: Int = 0,
  val maxRadius: Int = 5,

  val minYRadius: Int = 0,
  val maxYRadius: Int = 2,

  val density: Double = 0.55,              // base fill chance
  val edgeFalloff: Double = 1.5,           // >1 = denser center, thinner edges
  val applyChildChance: Boolean = false,
  val biomeCheck: Boolean = true,

  val chancePerPoint: Double = 0.2,
  val chanceSalt: Long = CruxMath.random().nextLong(),
  val coordinateSalt: Long = CruxMath.random().nextLong(),
  override val pass: DecorationPass = DecorationPass.SURFACE
) : VolumetricDecoration {

  init {
    require(minRadius >= 0) { "minRadius must be >= 0" }
    require(maxRadius >= minRadius) { "maxRadius must be >= minRadius" }
    require(minYRadius >= 0) { "minYRadius must be >= 0" }
    require(maxYRadius >= minYRadius) { "maxYRadius must be >= minYRadius" }
    require(density in 0.0..1.0) { "density must be between 0 and 1" }
    require(edgeFalloff > 0.0) { "edgeFalloff must be > 0" }
  }

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

  override fun findPlacement(
    region: LimitedRegion,
    point: VolumetricPropPoint,
    biomeBlend: BiomeBlendSample,
    biome: Biome
  ): Placement? {
    return Placed(
      seed = point.seed,
      x = point.worldX,
      y = point.worldY,
      z = point.worldZ
    )
  }

  override fun place(
    region: LimitedRegion,
    p: Placement,
    biomeBlend: BiomeBlendSample,
    biome: Biome
  ) {
    p as Placed
    if (decorations.isEmpty()) return

    val baseBiome = region.getBiome(p.x, p.y, p.z)

    val minRadiusSq = minRadius * minRadius
    val maxRadiusSq = maxRadius * maxRadius

    for (dx in -maxRadius..maxRadius) {
      for (dy in -maxYRadius..maxYRadius) {
        for (dz in -maxRadius..maxRadius) {
          val horizontalDist2 = dx * dx + dz * dz
          if (horizontalDist2 < minRadiusSq || horizontalDist2 > maxRadiusSq) continue
          if (abs(dy) !in minYRadius..maxYRadius) continue

          val x = p.x + dx
          val y = p.y + dy
          val z = p.z + dz

          if (!region.isInRegion(x, y, z)) continue
          if (biomeCheck && region.getBiome(x, y, z) != baseBiome) continue

          val horizontalNorm = if (maxRadius <= 0) 0.0
          else sqrt(horizontalDist2.toDouble()) / maxRadius.toDouble()

          val verticalNorm = if (maxYRadius <= 0) 0.0
          else abs(dy).toDouble() / maxYRadius.toDouble()

          // ellipsoid-style normalized distance
          val dist01 = ((horizontalNorm * horizontalNorm) + (verticalNorm * verticalNorm))
            .coerceAtMost(1.0)

          // stronger in center, weaker near edges
          val edgeWeight = (1.0 - dist01).coerceIn(0.0, 1.0)
          val localChance = density * edgeWeight.coerceIn(0.0, 1.0).let { w ->
            if (edgeFalloff == 1.0) w else Math.pow(w, edgeFalloff)
          }

          val pointSeed = mixSeed(p.seed, x, y, z, coordinateSalt)
          if (!chance(pointSeed, localChance)) continue

          val decorIndex = HashUtil.chooseInt(pointSeed xor 0x9E3779B974A7C15L, 0, decorations.lastIndex)
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
    }
  }

  data class Placed(
    val seed: Long,
    val x: Int,
    val y: Int,
    val z: Int
  ) : Placement
}
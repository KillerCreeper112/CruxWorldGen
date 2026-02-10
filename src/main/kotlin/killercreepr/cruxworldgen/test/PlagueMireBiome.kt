package killercreepr.cruxworldgen.test

import killercreepr.cruxworldgen.api.world.NoiseProvider
import org.bukkit.Material
import kotlin.math.abs

class PlagueMireBiome(private val noise: NoiseProvider) : AbyssBiome {

  override val temp = 0.7
  override val humidity = 0.6
  override val continental = 0.9
  override val erosion = 0.8
  override val weirdness = 0.5

  override val densityModifiers = listOf(
    DensityModifier { x, y, z, current ->
      // Big mountains
      val largeMountain = noise.noise2D(x * 0.005, z * 0.005) * 40
      val smallDetail = noise.noise2D(x * 0.02, z * 0.02) * 10
      val heightFactor = ((y - 50) / 100.0).coerceIn(0.0, 1.0)

      // Bias toward jagged peaks
      val jagged = abs(noise.noise2D(x * 0.01, z * 0.01)) * 15

      current + largeMountain * heightFactor + smallDetail + jagged
    }
  )

  override val surfaceRule = SurfaceRule { _, y, _, density, _ ->
    when {
      density > 30 -> Material.STONE
      density > 5  -> Material.DIRT
      else         -> Material.MYCELIUM
    }
  }
}

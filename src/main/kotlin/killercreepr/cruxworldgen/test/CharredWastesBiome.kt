package killercreepr.cruxworldgen.test

import killercreepr.cruxworldgen.api.world.NoiseProvider
import org.bukkit.Material

class CharredWastesBiome(private val noise: NoiseProvider) : AbyssBiome {
  override val temp = 0.3
  override val humidity = 0.2
  override val continental = 0.2
  override val erosion = 0.3
  override val weirdness = 0.1

  override val densityModifiers = listOf(
    DensityModifier { x, y, z, current ->
      // Gentle rolling hills
      val hill = noise.noise2D(x * 0.02, z * 0.02) * 5
      current + hill
    }
  )

  override val surfaceRule = SurfaceRule { _, y, _, density, _ ->
    if (density > 2) Material.MAGMA_BLOCK else Material.COAL_BLOCK
  }
}

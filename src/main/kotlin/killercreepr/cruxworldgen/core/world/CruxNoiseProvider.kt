package killercreepr.cruxworldgen.core.world

import killercreepr.cruxworldgen.api.world.NoiseProvider
import killercreepr.cruxgeneration.util.CruxNoise

class CruxNoiseProvider(val noise2D : CruxNoise, val noise3D : CruxNoise = noise2D) : NoiseProvider {
  override fun noise2D(x: Double, z: Double): Double = noise2D.noise(x, z)
  override fun noise3D(x: Double, y: Double, z: Double): Double = noise3D.noise(x, y,z)
}
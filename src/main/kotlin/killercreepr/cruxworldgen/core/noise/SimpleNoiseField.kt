package killercreepr.cruxworldgen.core.noise

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.noise.NoiseField

class SimpleNoiseField(val noise : CruxNoise) : NoiseField {
  override fun noise2D(x: Int, z: Int): Double = noise2D(x.toDouble(), z.toDouble())

  override fun noise3D(x: Int, y: Int, z: Int): Double = noise3D(x.toDouble(), y.toDouble(), z.toDouble())

  override fun noise2D(x: Double, z: Double): Double = noise.noise(x, z)

  override fun noise3D(x: Double, y: Double, z: Double): Double = noise.noise(x, y,z)
}
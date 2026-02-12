package killercreepr.cruxworldgen.test3

import killercreepr.cruxgeneration.util.CruxNoise

class SimplexNoise2D(
  private val noise: CruxNoise
) : Noise2D {
  override fun sample(x: Double, z: Double): Double {
    return noise.noise(x, z)
  }
}

class SimplexNoise3D(
  private val noise: CruxNoise,
  private val frequency: Double
) : Noise3D {
  override fun sample(x: Double, y: Double, z: Double): Double {
    return noise.noise(x, y, z)
  }
}

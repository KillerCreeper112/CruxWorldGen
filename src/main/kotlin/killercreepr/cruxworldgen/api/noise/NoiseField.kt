package killercreepr.cruxworldgen.api.noise

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.core.noise.SimpleNoiseField

interface NoiseField {
  companion object{
    fun noiseField(seed : Long, builder : CruxNoise.() -> Unit) : NoiseField{
      val noise = CruxNoise.fast(seed)
      builder.invoke(noise)
      return SimpleNoiseField(noise)
    }
  }

  fun noise2D(x: Int, z: Int): Double
  fun noise3D(x: Int, y: Int, z: Int): Double

  fun noise2D(x: Double, z: Double): Double
  fun noise3D(x: Double, y: Double, z: Double): Double
}
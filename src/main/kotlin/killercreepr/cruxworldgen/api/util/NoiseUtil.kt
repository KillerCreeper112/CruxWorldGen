package killercreepr.cruxworldgen.api.util

import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.NoiseKey
import kotlin.math.abs

object NoiseUtil {
  fun remap01(value : Double) : Double = (value + 1.0) * 0.5

  fun ridgedFbm3(
    ctx: GenerateContext,
    key: NoiseKey,
    shaper: NoiseShaper,
    x: Double, y: Double, z: Double,
    octaves: Int,
    lacunarity: Double = 2.0,
    gain: Double = 0.5
  ): Double {
    var amp = 1.0
    var freq = 1.0
    var sum = 0.0
    var norm = 0.0
    repeat(octaves) {
      val n = ctx.noise.get(key).noise3D(x * freq, y * freq, z * freq)
      val r = 1.0 - abs(shaper.shape(n))
      sum += r * amp
      norm += amp
      amp *= gain
      freq *= lacunarity
    }
    return if (norm <= 0.0) 0.0 else (sum / norm).coerceIn(0.0, 1.0)
  }

  fun densityBand01(baseDensity: Double, center: Double, halfWidth: Double): Double {
    // 1 at baseDensity=center, 0 outside [center-halfWidth .. center+halfWidth]
    val d = abs(baseDensity - center) / halfWidth
    val c = d.coerceIn(0.0, 1.0)
    val s = c * c * (3.0 - 2.0 * c)
    return 1.0 - s
  }

  fun fract(x: Double) = x - kotlin.math.floor(x)
}
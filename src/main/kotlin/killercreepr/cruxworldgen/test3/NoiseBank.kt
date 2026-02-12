package killercreepr.cruxworldgen.test3

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.core.Worley2D

class NoiseBank(
  val seed: Long,
  private val scale: Double = 1.0
) {

  private val biomeNoises = HashMap<String, Noise2D>()
  private val lowFreqNoises = HashMap<String, Noise2D>()
  private val detailNoises = HashMap<String, Noise2D>()
  private val cellularNoises = HashMap<String, Noise2D>()
  private val noise3D = HashMap<String, Noise3D>()

  private fun crux(seed: Long) =
    CruxNoise.fast(seed.toInt()).noiseType(CruxNoise.NoiseType.OpenSimplex2S)

  private fun fbm2D(seed: Long, octaves: Int) =
    crux(seed).fractalType(CruxNoise.FractalType.FBm).fractalOctaves(octaves).fractalLacunarity(2.0).fractalGain(0.5)

  private fun ridged2D(seed: Long, octaves: Int) =
    crux(seed).fractalType(CruxNoise.FractalType.Ridged).fractalOctaves(octaves).fractalLacunarity(2.0).fractalGain(0.55)

  private fun fbm3D(seed: Long, octaves: Int) =
    crux(seed)
      .rotationType3D(CruxNoise.RotationType3D.ImproveXZPlanes)
      .fractalType(CruxNoise.FractalType.FBm).fractalOctaves(octaves).fractalLacunarity(2.0).fractalGain(0.5)


  /* ---------------- BIOME NOISE ---------------- */

  fun biome2D(id: String, x: Int, z: Int): Double {
    val n = biomeNoises.getOrPut(id) {
      SimplexNoise2D(
        noise = fbm2D(seed xor id.hashCode().toLong(), octaves = 3).frequency(0.001)
      )
    }
    return normalize01(n.sample(x * scale, z * scale))
  }

  fun low2D(x: Int, z: Int): Double {
    val n = lowFreqNoises.getOrPut("low") {
      SimplexNoise2D(
        noise = fbm2D(seed + 1, octaves = 3).frequency(0.002)
      )
    }
    return normalize01(n.sample(x * scale, z * scale))
  }

  fun detail2D(x: Int, z: Int): Double {
    val n = detailNoises.getOrPut("detail") {
      SimplexNoise2D(
        noise = fbm2D(seed + 2, octaves = 4).frequency(0.03)
      )
    }
    return n.sample(x * scale, z * scale)
  }

  fun ridge2D(x: Int, z: Int): Double {
    val n = detailNoises.getOrPut("ridge") {
      SimplexNoise2D(
        noise = ridged2D(seed + 3, octaves = 4).frequency(0.005)
      )
    }
    return normalize01(n.sample(x * scale, z * scale)) // ridged fractal already "ridgey"
  }


  /* ---------------- CELLULAR / FEATURE NOISE ---------------- */

  fun cellular2D(x: Int, z: Int): Double {
    val n = cellularNoises.getOrPut("cellular") {
      val worley = Worley2D(seed + 4, 1.0 / 96.0)
      Noise2D { xx, zz -> worley.noiseF1(xx, zz) }
    }
    return normalize01(n.sample(x * scale, z * scale))
  }


  /* ---------------- 3D NOISE (CAVES / OVERHANGS) ---------------- */

  fun density3D(id: String, x: Int, y: Int, z: Int): Double {
    val n = noise3D.getOrPut(id) {
      SimplexNoise3D(
        noise = fbm3D(seed xor id.hashCode().toLong(), octaves = 3),
        frequency = 1.0 / 56.0
      )
    }
    return n.sample(x * scale, y * scale, z * scale)
  }

  /* ---------------- UTIL ---------------- */

  private fun normalize01(v: Double): Double = (v + 1.0) * 0.5
}

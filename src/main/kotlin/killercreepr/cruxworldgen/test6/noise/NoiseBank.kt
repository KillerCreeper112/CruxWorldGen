package killercreepr.cruxworldgen.test6.noise

import killercreepr.cruxgeneration.util.CruxNoise

class NoiseBank(
  val seed : Long
) {
  private val temperature: CruxNoise = CruxNoise.fast(seed.toInt())
    .frequency(0.005)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(5)
  private val humidity: CruxNoise = CruxNoise.fast(seed.toInt())
    .frequency(0.002)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(7)
  private val continental: CruxNoise = CruxNoise.fast(seed.toInt())
    .frequency(0.01)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(3)
  private val weirdness: CruxNoise = CruxNoise.fast(seed.toInt())
    .frequency(0.03)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(5)

  fun zone2D(x : Int, z : Int) : Double = CruxNoise.fast(seed.toInt())
    .frequency(0.001)
    .fractalOctaves(2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalLacunarity(2.0)
    .fractalGain(0.5)
    .noise(x.toDouble(), z.toDouble())

  fun biome3D(x : Int, y : Int, z : Int) : Double = CruxNoise.fast(seed.toInt())
    .frequency(0.005)
    .fractalOctaves(3)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalLacunarity(2.0)
    .fractalGain(0.5)
    .noise(x.toDouble(), y.toDouble(),z.toDouble())

  fun temperature(x : Int, y : Int, z : Int) : Double = temperature.noise(x.toDouble(), y.toDouble(), z.toDouble())
  fun humidity(x : Int, y : Int, z : Int) : Double = humidity.noise(x.toDouble(), y.toDouble(),z.toDouble())
  fun continental(x : Int, y : Int, z : Int) : Double = continental.noise(x.toDouble(), y.toDouble(),z.toDouble())
  fun weirdness(x : Int, y : Int, z : Int) : Double = weirdness.noise(x.toDouble(), y.toDouble(),z.toDouble())

  private val terrainHeightNoise = CruxNoise.fast(seed.toInt())
    .frequency(0.001)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(4)

  private val terrainDetailNoise = CruxNoise.fast(seed.toInt())
    .frequency(0.02)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(3)

  fun plainsHeight2D(worldX: Int, worldZ: Int): Double {
    return terrainHeightNoise.noise(worldX.toDouble(), worldZ.toDouble()) // ~[-1..1]
  }

  fun detail3D(worldX: Int, y: Int, worldZ: Int): Double {
    return terrainDetailNoise.noise(worldX.toDouble(), y.toDouble(), worldZ.toDouble()) // ~[-1..1]
  }

  // In NoiseBank

  private val mountainBase2D = CruxNoise.fast(seed.toInt())
    .frequency(0.006)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(5)

  private val mountainRidge2D = CruxNoise.fast(seed.toInt() xor 0x5F356495)
    .frequency(0.0012)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(4)

  fun mountainBaseHeight2D(worldX: Int, worldZ: Int): Double {
    return mountainBase2D.noise(worldX.toDouble(), worldZ.toDouble()) // ~[-1..1]
  }

  fun mountainRidge2D(worldX: Int, worldZ: Int): Double {
    return mountainRidge2D.noise(worldX.toDouble(), worldZ.toDouble()) // ~[-1..1]
  }

  // In NoiseBank

  private val plateauMask2D = CruxNoise.fast(seed.toInt() xor 0x1A2B3C4D)
    .frequency(0.0012)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(3)

  private val plateauVariation2D = CruxNoise.fast(seed.toInt() xor 0x55AA7711)
    .frequency(0.003)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(4)

  fun plateauMask2D(worldX: Int, worldZ: Int): Double {
    return plateauMask2D.noise(worldX.toDouble(), worldZ.toDouble()) // ~[-1..1]
  }

  fun plateauVariation2D(worldX: Int, worldZ: Int): Double {
    return plateauVariation2D.noise(worldX.toDouble(), worldZ.toDouble()) // ~[-1..1]
  }

  // NoiseBank additions

  private val fjordFlow2D: CruxNoise = CruxNoise.fast(seed.toInt() xor 0x4A11C0DE)
    .frequency(0.0012)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(3)

  private val fjordLines2D: CruxNoise = CruxNoise.fast(seed.toInt() xor 0x17D00D5)
    .frequency(0.0045)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(4)

  fun fjordFlow2D(worldX: Int, worldZ: Int): Double {
    return fjordFlow2D.noise(worldX.toDouble(), worldZ.toDouble()) // ~[-1..1]
  }

  fun fjordLines2D(worldX: Int, worldZ: Int): Double {
    return fjordLines2D.noise(worldX.toDouble(), worldZ.toDouble()) // ~[-1..1]
  }


  data class SwirlCenter(val centerX: Int, val centerZ: Int)

  fun swirlCenter(worldSeed: Long, worldX: Int, worldZ: Int, cellSizeBlocks: Int): SwirlCenter {
    val cellX = kotlin.math.floor(worldX.toDouble() / cellSizeBlocks.toDouble()).toInt()
    val cellZ = kotlin.math.floor(worldZ.toDouble() / cellSizeBlocks.toDouble()).toInt()

    val hash = hash2D(worldSeed, cellX, cellZ)
    val offsetX = (hash and 0xFFFF).toInt() % cellSizeBlocks
    val offsetZ = ((hash ushr 16) and 0xFFFF).toInt() % cellSizeBlocks

    val baseX = cellX * cellSizeBlocks
    val baseZ = cellZ * cellSizeBlocks
    return SwirlCenter(baseX + offsetX, baseZ + offsetZ)
  }
  private val HASH_SALT: Long = -7046029254386353131L
  private val HASH_MUL_X: Long = 7145483588892929177L
  private val HASH_MIX_1: Long = -4658895280553007687L
  private val HASH_MIX_2: Long = -7723592293110705685L

  private fun hash2D(seed: Long, x: Int, z: Int): Long {
    var value = seed
    value = value xor (x.toLong() * HASH_MUL_X)
    value = value xor (z.toLong() * HASH_SALT)
    value = (value xor (value ushr 30)) * HASH_MIX_1
    value = (value xor (value ushr 27)) * HASH_MIX_2
    return value xor (value ushr 31)
  }
}
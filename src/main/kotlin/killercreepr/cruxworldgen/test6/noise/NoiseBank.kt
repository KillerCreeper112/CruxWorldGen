/*
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


  private val terrainDetailNoise01 = CruxNoise.fast(seed.toInt())
    .frequency(0.005)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(1)

  fun terrainDetailNoise01(worldX: Int, y: Int, worldZ: Int): Double {
    return terrainDetailNoise01.noise(worldX.toDouble(), y.toDouble(), worldZ.toDouble()) // ~[-1..1]
  }

  private val charredBase = CruxNoise.fast(s(0xC11A_7ED1))
    .frequency(0.0014) // big rolling, ~700 block wavelength
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(2)
    .fractalGain(0.5)
    .fractalLacunarity(2.0)

  // Ridgy uplift / knuckles (use ridged fractal so it naturally forms ridges)
  private val charredRidge = CruxNoise.fast(s(0xA55E_0001))
    .frequency(0.0024)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.Ridged)
    .fractalOctaves(2)
    .fractalGain(0.5)
    .fractalLacunarity(2.0)

  // ===== Cracks: warp + mask =====
  private val charredCrackWarp = CruxNoise.fast(s(0xBADC_1201))
    .frequency(0.0028)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(1)

  // Crack mask: slightly higher frequency to get lots of fracture lines
  private val charredCrackMask = CruxNoise.fast(s(0xBADC_1202))
    .frequency(0.018)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(1)

  // ===== Fissures: warp + mask =====
  private val charredFissureWarp = CruxNoise.fast(s(0xF155_0001))
    .frequency(0.0018)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(1)

  private fun s(salt: Long): Int = (seed xor salt).toInt()
  // Fissure mask:
  // Perlin often gives nicer long-ish “bands” when you do ridge = 1-abs(n)
  private val charredFissureMask = CruxNoise.fast(s(0xF155_0002))
    .frequency(0.0065) // lower = longer, fewer fissures
    .noiseType(CruxNoise.NoiseType.Perlin)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(1)

  // ===== Public sampling functions (match what your biome expects) =====

  fun charredBase2D(x: Int, z: Int): Double =
    charredBase.noise(x.toDouble(), z.toDouble()) // [-1..1]

  fun charredRidge2D(x: Int, z: Int): Double =
    charredRidge.noise(x.toDouble(), z.toDouble()) // [-1..1]

  fun charredCrackWarp2D(x: Double, z: Double): Double =
    charredCrackWarp.noise(x, z) // [-1..1]

  fun charredCrackMask2D(x: Double, z: Double): Double =
    charredCrackMask.noise(x, z) // [-1..1]

  fun charredFissureWarp2D(x: Double, z: Double): Double =
    charredFissureWarp.noise(x, z) // [-1..1]

  fun charredFissureMask2D(x: Double, z: Double): Double =
    charredFissureMask.noise(x, z) // [-1..1]

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

  // NoiseBank additions

  private val caveWarp3D: CruxNoise = CruxNoise.fast(seed.toInt() xor 0x51C0FFEE)
    .frequency(0.018)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(2)

  private val caveWorm3D: CruxNoise = CruxNoise.fast(seed.toInt() xor 0xC0A7E123.toInt())
    .frequency(0.008)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(1)

  private val cavern3D: CruxNoise = CruxNoise.fast(seed.toInt() xor 0xCA7E0001.toInt())
    .frequency(0.005) // lower frequency = bigger rooms
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(1)

  fun caveWarp3D(worldX: Int, y: Int, worldZ: Int): Double =
    caveWarp3D.noise(worldX.toDouble(), y.toDouble(), worldZ.toDouble()) // ~[-1..1]

  fun caveWorm3D(worldX: Int, y: Int, worldZ: Int): Double =
    caveWorm3D.noise(worldX.toDouble(), y.toDouble(), worldZ.toDouble()) // ~[-1..1]

  fun cavern3D(worldX: Int, y: Int, worldZ: Int): Double =
    cavern3D.noise(worldX.toDouble(), y.toDouble(), worldZ.toDouble()) // ~[-1..1]

  private val spaghettiHeight2D: CruxNoise =
    CruxNoise.fast(seed.toInt() xor 0x5A6A771)
      .frequency(0.0025)
      .noiseType(CruxNoise.NoiseType.OpenSimplex2)
      .fractalType(CruxNoise.FractalType.FBm)
      .fractalOctaves(2)
  fun spaghettiHeight2D(x: Int, z: Int): Double =
    spaghettiHeight2D.noise(x.toDouble(), z.toDouble())

  private val cheese3D: CruxNoise =
    CruxNoise.fast(seed.toInt() xor 0x0CEE5EED)
      .frequency(0.010)
      .noiseType(CruxNoise.NoiseType.OpenSimplex2)
      .fractalType(CruxNoise.FractalType.FBm)
      .fractalOctaves(1)

  fun cheese3D(x: Int, y: Int, z: Int): Double =
    cheese3D.noise(x.toDouble(), y.toDouble(), z.toDouble())

  // NoiseBank
  val pillarPatch2D = CruxNoise.fast(seed.toInt() xor 0x71A12B3)
    .frequency(0.004) // lower = bigger patches
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(2)

  val pillarDetail3D = CruxNoise.fast(seed.toInt() xor 0x19C0D11)
    .frequency(0.08) // higher = roughness
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(1)
  val pillar2D = CruxNoise.fast(seed.toInt() xor 0x071A12B3)
    .frequency(0.0025) // ⭐ bigger patches than 0.004
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(3) // ⭐ slightly richer blobs than 2


  val pillarHeight2D = CruxNoise.fast(seed.toInt() xor 0x019C0D11)
    .frequency(0.010) // ⭐ much lower than 0.08
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(2)



  fun pillar2D(x: Int, z: Int): Double =
    pillar2D.noise(x.toDouble(), z.toDouble())
  fun pillarHeight2D(x: Int,  z: Int): Double =
    pillarHeight2D.noise(x.toDouble(),  z.toDouble())

  // In NoiseBank (or wherever you keep CruxNoise)

  val ravineMask2D = CruxNoise.fast(seed.toInt() xor 0x71A1_2B3)
    .frequency(0.0018) // lower = longer ravines
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(2)

  val ravineWarp2D = CruxNoise.fast(seed.toInt() xor 0x4A9B_1D77)
    .frequency(0.008)  // higher = wigglier
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(1)

  val ravineVar2D = CruxNoise.fast(seed.toInt() xor 0x19C0_D11)
    .frequency(0.0035) // controls width/depth patches
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(1)

  val ravineBridge2D = CruxNoise.fast(seed.toInt() xor 0x19C0_D11)
    .frequency(0.0016) // controls width/depth patches
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(1)



  fun ravineVar2D(x: Double, z: Double): Double =
    ravineVar2D.noise(x.toDouble(), z.toDouble())
  fun ravineWarp2D(x: Double,  z: Double): Double =
    ravineWarp2D.noise(x.toDouble(),  z.toDouble())
  fun ravineMask2D(x: Double,  z: Double): Double =
    ravineMask2D.noise(x.toDouble(),  z.toDouble())
  fun ravineBridge2D(x: Double,  z: Double): Double{

    val n = ravineBridge2D.noise(x, z)      // [-1, 1]
    val ridge = 1.0 - kotlin.math.abs(n)    // [0, 1], high near centerline
    // optional: sharpen
    return (ridge * ridge).coerceIn(0.0, 1.0)
  }

  // NoiseBank.kt
  val basinWarp2D = CruxNoise.fast(seed.toInt() xor 0x71A12B3)
    .frequency(0.0016) // very low => broad warps
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(2)

  val basinMask2D = CruxNoise.fast(seed.toInt() xor 0x19C0_D11)
    .frequency(0.0028) // basin patch size (lower => bigger basins)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(3)

  val basinFloor2D = CruxNoise.fast(seed.toInt() xor 0x2C0F_EE5)
    .frequency(0.03) // floor imperfections
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(2)

  val fungalWarp2D = CruxNoise.fast(seed.toInt() xor 0x51D11A7)
    .frequency(0.0018)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(2)

  // makes "thin pillar ribbons": ridge = 1 - abs(noise)
  val fungalPillarRibbon2D = CruxNoise.fast(seed.toInt() xor 0x0BADC0FF)
    .frequency(0.0042)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(1)

  // varies pillar height along the ribbons
  val fungalPillarVar2D = CruxNoise.fast(seed.toInt() xor 0x19C0_D11)
    .frequency(0.010)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(2)

  // 3D “edge noise” that helps pillars stay thin and not perfectly cylindrical
  val fungalPillarEdge3D = CruxNoise.fast(seed.toInt() xor 0x2C0F_EE5)
    .frequency(0.060)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(1)

  // 2D used to break caps into patches (so not every pillar gets a hat)
  val fungalCapPatch2D = CruxNoise.fast(seed.toInt() xor 0x71A12B3)
    .frequency(0.0065)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(1)

  // 2D irregularity for cap edges
  val fungalCapEdge2D = CruxNoise.fast(seed.toInt() xor 0x7F4A_7C15.toInt())
    .frequency(0.030)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(2)

  // NoiseBank.kt

  val mireBubblePatch2D = CruxNoise.fast(seed.toInt() xor 0x0BADC0FE)
    .frequency(0.0032) // lower = bigger patches
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(2)

  val mireBubbleVar2D = CruxNoise.fast(seed.toInt() xor 0x6D2B79F5)
    .frequency(0.020) // higher = more local variation
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(1)

  // NoiseBank.kt (add fields similar to your other noises)

  val mireBase2D = CruxNoise.fast(seed.toInt() xor 0x52A11E3)
    .frequency(0.0025) // broad rise/fall
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(2)

  val mireBubbleWarp2D = CruxNoise.fast(seed.toInt() xor 0x19C0_D11)
    .frequency(0.010) // warp field
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(1)

  val mireBubbleCells2D = CruxNoise.fast(seed.toInt() xor 0x71A12B3)
    .frequency(0.040) // bubble size (0.03..0.06)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(1)

  val mireBubbleSign2D = CruxNoise.fast(seed.toInt() xor 0x2D7A_113)
    .frequency(0.040) // match bubbleCells2D
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(1)

  // Mountains (2D)
  val mireHighlandsBase2D = CruxNoise.fast(seed.toInt() xor 0x6A11_02D)
    .frequency(0.005) // broad uplift
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(2)

  val mireHighlandsRidge2D = CruxNoise.fast(seed.toInt() xor 0xB16B_00B)
    .frequency(0.003) // ridges
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(1)

  // Overhangs (3D)
  val mireOverhang3D = CruxNoise.fast(seed.toInt() xor 0x0C0FFEE) // pick any int literal you like
    .frequency(0.02) // 0.015..0.030 : size of overhang pockets
    .noiseType(CruxNoise.NoiseType.OpenSimplex2) // or OpenSimplex2S if you have it
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(2)

  // Optional: warp the overhang field so it looks less “noise-ball”
  val mireOverhangWarp2D = CruxNoise.fast(seed.toInt() xor 0x7A11_0EED)
    .frequency(0.009)
    .noiseType(CruxNoise.NoiseType.OpenSimplex2)
    .fractalType(CruxNoise.FractalType.FBm)
    .fractalOctaves(1)


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
}*/

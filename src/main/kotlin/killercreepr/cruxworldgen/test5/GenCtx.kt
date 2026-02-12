/*
@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package killercreepr.cruxworldgen.better

import killercreepr.cruxgeneration.util.CruxNoise
import org.bukkit.Material
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.WorldInfo
import java.util.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow

*/
/**
 * Single-file modular worldgen system (code-only, no configs):
 * - Seamless biome blending (2D weights) + optional 3D underground biome influence
 * - One density field: base height + blended detail - blended carvers + blended additives
 * - Hydrology pass: ocean/sea connectivity flood fill + aquifer pockets biased by biome wetness
 * - Materials pass: per-biome surface painting (after solidity + water)
 *
 * Minecraft constraint note:
 * ChunkData.setBlock expects x,z in 0..15. You can keep chunkWidth in settings,
 * but it must be 16 when placing blocks.
 *//*

class BetterWorldGen(
  private val settings: Settings = Settings()
) : ChunkGenerator() {

  // Built once per world seed
  private var graph: WorldGraph? = null

  override fun generateNoise(
    worldInfo: WorldInfo,
    random: Random,
    chunkX: Int,
    chunkZ: Int,
    chunkData: ChunkData
  ) {
    val seed = worldInfo.seed
    val minY = chunkData.minHeight
    val maxY = chunkData.maxHeight

    val g = graph ?: WorldGraph(seed, settings).also { graph = it }
    val ctx = Ctx(seed, settings, chunkX, chunkZ, minY, maxY)

    require(settings.chunkWidth == 16) {
      "settings.chunkWidth must be 16 for Bukkit ChunkData (x,z must be in 0..15)."
    }

    val w = settings.chunkWidth
    val sea = settings.seaLevel.coerceIn(minY, maxY)

    // Cache per-column 2D weights + blended surface height
    val weights2D = Array(w * w) { BiomeWeights.EMPTY }
    val height2D = DoubleArray(w * w)

    for (lx in 0 until w) {
      val wx = chunkX * w + lx
      for (lz in 0 until w) {
        val wz = chunkZ * w + lz
        val idx = lx + lz * w
        val bw = g.resolver.weights2D(ctx, wx, wz)
        weights2D[idx] = bw
        height2D[idx] = g.density.blendedHeight(ctx, bw, wx, wz)
      }
    }

    // PASS 1: Solidity (stone / air) from density
    // We intentionally do NOT place water here to avoid "all caves flood" by default.
    for (lx in 0 until w) {
      val wx = chunkX * w + lx
      for (lz in 0 until w) {
        val wz = chunkZ * w + lz
        val idx = lx + lz * w
        val bw2 = weights2D[idx]
        val h = height2D[idx]

        for (y in maxY downTo minY) {
          val bw3 = g.resolver.weights3D(ctx, bw2, wx, y, wz)
          val d = g.density.densityAt(ctx, bw3, h, wx, y, wz)

          if (d > 0.0) {
            chunkData.setBlock(lx, y, lz, settings.baseStone)
          } else {
            // leave air for now
          }
        }
      }
    }

    // PASS 2: Hydrology (water placement)
    // Strategy:
    //   A) Sea flood-fill: only air cells at/below sea that are connected to outside/sky/edge become water.
    //   B) Aquifers: isolated pockets of water underground based on 3D noise + biome wetness.
    //
    // We operate on a compact y-range [minY..sea] only (that’s where water matters).
    val seaRange = sea - minY + 1
    if (seaRange > 0) {
      val size = w * w * seaRange
      val air = BooleanArray(size)           // air cell at/below sea
      val seaFill = BooleanArray(size)       // connected to sea
      val queue = ArrayDeque<Int>(4096)

      fun index(lx: Int, y: Int, lz: Int): Int {
        val yy = y - minY
        return (yy * w + lz) * w + lx
      }

      fun trySeed(lx: Int, y: Int, lz: Int) {
        val i = index(lx, y, lz)
        if (!air[i] || seaFill[i]) return
        seaFill[i] = true
        queue.add(i)
      }

      // Build air mask (below sea): include air; ignore stone/water for now
      for (lx in 0 until w) {
        for (lz in 0 until w) {

          // Seed: open-to-sky columns down to sea
          for (lx in 0 until w) for (lz in 0 until w) {
            var openToSky = true
            for (yy in maxY downTo (sea + 1)) {
              if (chunkData.getType(lx, yy, lz) != Material.AIR) { openToSky = false; break }
            }
            if (!openToSky) continue

            for (yy in sea downTo minY) {
              if (chunkData.getType(lx, yy, lz) == Material.AIR) { trySeed(lx, yy, lz); break }
            }
          }


          for (y in minY..sea) {
            val m = chunkData.getType(lx, y, lz)
            if (m == Material.AIR) air[index(lx, y, lz)] = true
          }
        }
      }

      // Seed flood fill from:
      // 1) chunk borders (assume connected to outside ocean/space)
      // 2) columns open-to-sky down to sea (connected via sky water surface)
      //
      // This is not a perfect "ocean network" across chunks, but it produces the right feel and is stable.

      // Seed #1: borders at/below sea
      for (y in minY..sea) {
        for (t in 0 until w) {
          trySeed(0, y, t)
          trySeed(w - 1, y, t)
          trySeed(t, y, 0)
          trySeed(t, y, w - 1)
        }
      }

      // Seed #2: open-to-sky columns down to sea (no solid above sea)
      // We detect open-to-sky by scanning from maxY down to sea+1 for any non-air.
      for (lx in 0 until w) {
        val wx = chunkX * w + lx
        for (lz in 0 until w) {
          val wz = chunkZ * w + lz
          var openToSky = true
          for (y in maxY downTo (sea + 1)) {
            val m = chunkData.getType(lx, y, lz)
            if (m != Material.AIR) { openToSky = false; break }
          }
          if (openToSky) {
            // seed the topmost air cell at/below sea (if any)
            for (y in sea downTo minY) {
              if (chunkData.getType(lx, y, lz) == Material.AIR) {
                trySeed(lx, y, lz)
                break
              }
            }
          }
        }
      }

      // Flood fill within chunk (6-neighbor)
      while (queue.isNotEmpty()) {
        val cur = queue.removeFirst()
        val lx = cur % w
        val tmp = cur / w
        val lz = tmp % w
        val yy = tmp / w
        val y = yy + minY

        fun push(nx: Int, ny: Int, nz: Int) {
          if (nx !in 0 until w || nz !in 0 until w) return
          if (ny < minY || ny > sea) return
          val ni = index(nx, ny, nz)
          if (!air[ni] || seaFill[ni]) return
          seaFill[ni] = true
          queue.add(ni)
        }

        push(lx + 1, y, lz)
        push(lx - 1, y, lz)
        push(lx, y, lz + 1)
        push(lx, y, lz - 1)
        push(lx, y + 1, lz)
        push(lx, y - 1, lz)
      }

      // Place water:
      // - If connected-to-sea AND dominant biome allows sea flooding, fill with water
      // - Else, maybe aquifer water (noise-based) if biome wetness supports it
      for (lx in 0 until w) {
        val wx = chunkX * w + lx
        for (lz in 0 until w) {
          val wz = chunkZ * w + lz
          val idx2 = lx + lz * w
          val bw2 = weights2D[idx2]
          val dom = bw2.dominantSurfaceBiome()
          val wetness = bw2.blendedWetness()
          val floodsFromSea = dom.hydrology.floodsFromSea

          for (y in minY..sea) {
            val i = index(lx, y, lz)
            if (!air[i]) continue // only fill air cells

            val seaConnected = seaFill[i] && floodsFromSea
            val aquifer = g.hydro.isAquiferWater(ctx, wx, y, wz, wetness)

            if (seaConnected || aquifer) {
              chunkData.setBlock(lx, y, lz, settings.water)
            }
          }
        }
      }
    }

    // PASS 3: Surface paint (dominant *surface* biome decides)
    for (lx in 0 until w) {
      val wx = chunkX * w + lx
      for (lz in 0 until w) {
        val wz = chunkZ * w + lz
        val idx = lx + lz * w
        val bw2 = weights2D[idx]
        val dom = bw2.dominantSurfaceBiome()

        // Find topmost solid (ignoring water)
        var topY = Int.MIN_VALUE
        for (y in maxY downTo minY) {
          val m = chunkData.getType(lx, y, lz)
          if (m != Material.AIR && m != settings.water) { topY = y; break }
        }
        if (topY == Int.MIN_VALUE) continue

        dom.materials.paintColumn(
          ctx = ctx,
          wx = wx,
          wz = wz,
          topY = topY,
          seaLevel = sea,
          get = { yy -> chunkData.getType(lx, yy, lz) },
          set = { yy, mat -> chunkData.setBlock(lx, yy, lz, mat) }
        )
      }
    }
  }

  */
/* ============================================================
   * Settings / Context
   * ============================================================ *//*


  data class Settings(
    val chunkWidth: Int = 16,
    val seaLevel: Int = 64,
    val verticalScale: Double = 10.0,
    val baseStone: Material = Material.STONE,
    val water: Material = Material.WATER
  )

  data class Ctx(
    val seed: Long,
    val settings: Settings,
    val chunkX: Int,
    val chunkZ: Int,
    val minY: Int,
    val maxY: Int
  )

  */
/* ============================================================
   * WorldGraph: composition root
   * ============================================================ *//*


  class WorldGraph(seed: Long, settings: Settings) {
    val noise = CruxNoiseBank(seed)
    val fields = Fields(noise, settings.seaLevel)
    val caves: CaveEngine = CaveEngine(noise)
    val biomes: List<Biome> = listOf(
      PlainsBiome(noise, fields),
      MountainBiome(noise, fields),
      //SpikyCavernsBiome(noise, fields) // 3D underground influence; also controls wet caves/spikes
    )

    val resolver: BiomeResolver = DefaultResolver(fields, biomes)
    val density: DensityEngine = DensityEngine(fields, noise, caves)
    val hydro: HydrologyEngine = HydrologyEngine(noise)
  }

  */
/* ============================================================
   * Noise (deterministic keyed fbm)
   * ============================================================ *//*


  class CruxNoiseBank(private val worldSeed: Long) {
    private val cache = HashMap<String, CruxNoise>()

    fun get(key: String, configure: CruxNoise.() -> Unit): CruxNoise {
      return cache.getOrPut(key) {
        val s = mixSeed(worldSeed, key)
        CruxNoise.fast(s).apply(configure)
      }
    }

    fun n2(key: String, x: Int, z: Int, configure: CruxNoise.() -> Unit): Double =
      get(key, configure).noise(x.toDouble(), z.toDouble())

    fun n3(key: String, x: Int, y: Int, z: Int, configure: CruxNoise.() -> Unit): Double =
      get(key, configure).noise(x.toDouble(), y.toDouble(), z.toDouble())

    */
/** Convenience: FBm 2D *//*

    fun fbm2(key: String, x: Int, z: Int, freq: Double, octaves: Int, type: CruxNoise.NoiseType = CruxNoise.NoiseType.OpenSimplex2): Double =
      n2(key, x, z) {
        noiseType(type)
        frequency(freq)
        fractalType(CruxNoise.FractalType.FBm)
        fractalOctaves(octaves)
        fractalLacunarity(2.0)
        fractalGain(0.5)
      }

    */
/** Convenience: FBm 3D *//*

    fun fbm3(key: String, x: Int, y: Int, z: Int, freq: Double, octaves: Int, type: CruxNoise.NoiseType = CruxNoise.NoiseType.OpenSimplex2S): Double =
      n3(key, x, y, z) {
        noiseType(type)
        frequency(freq)
        fractalType(CruxNoise.FractalType.FBm)
        fractalOctaves(octaves)
        fractalLacunarity(2.0)
        fractalGain(0.5)
      }

    */
/** Convenience: Ridged 2D (returns ~[-1,1] from the library; we convert to [0,1] ridge shape) *//*

    fun ridged2(key: String, x: Int, z: Int, freq: Double, octaves: Int, type: CruxNoise.NoiseType = CruxNoise.NoiseType.OpenSimplex2S): Double {
      val n = n2(key, x, z) {
        noiseType(type)
        frequency(freq)
        fractalType(CruxNoise.FractalType.Ridged)
        fractalOctaves(octaves)
        fractalLacunarity(2.0)
        fractalGain(0.5)
      }
      // Make it behave like my earlier ridged2 helper: [0..1]
      val r = 1.0 - kotlin.math.abs(n)
      return (r * r).coerceIn(0.0, 1.0)
    }

    */
/** Domain warp a 2D point and return warped coords *//*

    fun warp2(key: String, x: Int, z: Int, freq: Double, strength: Double): Pair<Double, Double> {
      val v = CruxNoise.Vector2(x.toDouble(), z.toDouble())
      get(key) {
        frequency(freq)
        domainWarpType(CruxNoise.DomainWarpType.OpenSimplex2)
        domainWarpAmp(strength)
      }.domainWarp(v)
      return v.x to v.y
    }

    private fun mixSeed(seed: Long, key: String): Int {
      var h = (seed xor (seed ushr 32)).toInt()
      for (c in key) {
        h = h xor c.code
        h *= 0x45d9f3b
        h = h xor (h ushr 16)
      }
      return h
    }
  }


  */
/* ============================================================
   * Fields: global continuity signals
   * ============================================================ *//*


  class Fields(val bank: CruxNoiseBank, val seaLevel: Int) {

    fun continental(x: Int, z: Int): Double {
      // Use domain warp by warping a vector, then sampling.
      val wxz = domainWarp2("contWarp", x.toDouble(), z.toDouble(), freq = 0.0005, amp = 120.0)

      return bank.n2("continental", wxz.first.toInt(), wxz.second.toInt()) {
        frequency(0.00025)
        noiseType(CruxNoise.NoiseType.OpenSimplex2)
        fractalType(CruxNoise.FractalType.FBm)
        fractalOctaves(4)
        fractalLacunarity(2.0)
        fractalGain(0.5)
      }
    }

    fun globalBaseHeight(x: Int, z: Int): Double {
      val c = continental(x, z) // usually ~[-1,1]
      val land = smoothstep(-0.15, 0.25, c) // 0..1

      val broad = bank.n2("broadHeight", x, z) {
        frequency(0.0006)
        noiseType(CruxNoise.NoiseType.OpenSimplex2)
        fractalType(CruxNoise.FractalType.FBm)
        fractalOctaves(3)
      }

      val hills = bank.n2("broadHills", x, z) {
        frequency(0.0012)
        noiseType(CruxNoise.NoiseType.OpenSimplex2S)
        fractalType(CruxNoise.FractalType.FBm)
        fractalOctaves(3)
      }

      return seaLevel + land * (18.0 + broad * 14.0 + hills * 10.0)
    }

    fun slopeApprox(x: Int, z: Int): Double {
      val h = globalBaseHeight(x, z)
      val hx = globalBaseHeight(x + 8, z)
      val hz = globalBaseHeight(x, z + 8)
      return max(abs(hx - h), abs(hz - h)) / 8.0
    }

    fun domainWarp2(key: String, x: Double, z: Double, freq: Double, amp: Double): Pair<Double, Double> {
      val v = CruxNoise.Vector2(x, z)
      bank.get(key) {
        frequency(freq)
        domainWarpType(CruxNoise.DomainWarpType.OpenSimplex2)
        domainWarpAmp(amp)
      }.domainWarp(v)
      return v.x to v.y
    }
  }

  */
/* ============================================================
   * Biomes / Profiles
   * ============================================================ *//*


  data class HydrologyProfile(
    val wetness: Double,          // 0..1 (higher -> more aquifer water)
    val floodsFromSea: Boolean    // if false, caves won't flood from sea connectivity
  )

  interface MaterialProfile {
    fun paintColumn(
      ctx: Ctx,
      wx: Int,
      wz: Int,
      topY: Int,
      seaLevel: Int,
      get: (Int) -> Material,
      set: (Int, Material) -> Unit
    )
  }

  interface TerrainProfile {
    fun suitability2D(ctx: Ctx, x: Int, z: Int): Double
    fun suitability3D(ctx: Ctx, x: Int, y: Int, z: Int): Double = 1.0

    fun height(ctx: Ctx, x: Int, z: Int): Double
    fun detail(ctx: Ctx, x: Int, y: Int, z: Int): Double = 0.0
    fun carve(ctx: Ctx, x: Int, y: Int, z: Int): Double = 0.0  // subtract from density
    fun add(ctx: Ctx, x: Int, y: Int, z: Int): Double = 0.0    // add to density
  }

  interface Biome {
    val id: String
    val isSurfaceBiome: Boolean
    val terrain: TerrainProfile
    val materials: MaterialProfile
    val hydrology: HydrologyProfile
    val caves: CaveProfile?
  }

  class PlainsBiome(noise: CruxNoiseBank, fields: Fields) : Biome {
    override val id = "plains"
    override val isSurfaceBiome = true

    override val hydrology = HydrologyProfile(wetness = 0.35, floodsFromSea = true)

    override val caves = CaveProfile(
      strengthMin = 1.4, strengthMax = 10.0,
      bandShallow = 0.33, bandDeep = 0.26, bandWidth = 0.1,
      freq = 0.030, octaves = 3,
      surfaceBuffer = 26.0,
      depthTop = fields.seaLevel + 10,
      depthBottom = fields.seaLevel - 150
    )

    override val materials: MaterialProfile = SimpleSurface(
      top = Material.LIME_TERRACOTTA,
      under = Material.GREEN_WOOL,
      underDepth = 4
    )

    override val terrain: TerrainProfile = object : TerrainProfile {
      override fun suitability2D(ctx: Ctx, x: Int, z: Int): Double {
        val slope = fields.slopeApprox(x, z)
        val mask = noise.fbm2("biomeMask", x, z, freq = 0.0007, octaves = 3) * 0.5 + 0.5
        val slopePref = (1.0 - smoothstep(0.9, 2.2, slope)).coerceIn(0.0, 1.0)
        val maskPref = (1.0 - mask).coerceIn(0.0, 1.0)
        return (0.55 * slopePref + 0.45 * maskPref).coerceIn(0.0, 1.0)
      }

      override fun height(ctx: Ctx, x: Int, z: Int): Double {
        val base = fields.globalBaseHeight(x, z)
        val gentle = noise.fbm2("plainsH", x, z, freq = 0.0014, octaves = 3)
        return base + gentle * 6.0
      }

      override fun detail(ctx: Ctx, x: Int, y: Int, z: Int): Double {
        val d = noise.fbm3("plainsD", x, y, z, freq = 0.02, octaves = 2)
        return d * 0.07
      }
    }
  }

  class MountainBiome(noise: CruxNoiseBank, fields: Fields) : Biome {
    override val id = "mountains"
    override val isSurfaceBiome = true

    override val hydrology = HydrologyProfile(wetness = 0.25, floodsFromSea = true)

    override val caves = CaveProfile(
      strengthMin = 1.0, strengthMax = 14.0,
      bandShallow = 0.30, bandDeep = 0.18, bandWidth = 0.10,
      freq = 0.020, octaves = 2,
      surfaceBuffer = 34.0,
      depthTop = fields.seaLevel + 5,
      depthBottom = -64 + 10//todo make -64 minY level from fields
    )

    override val materials: MaterialProfile = MountainSurface(
      dirtDepth = 3,
      snowLine = 130
    )

    override val terrain: TerrainProfile = object : TerrainProfile {
      override fun suitability2D(ctx: Ctx, x: Int, z: Int): Double {
        val mask = noise.fbm2("biomeMask", x, z, freq = 0.0007, octaves = 3) * 0.5 + 0.5
        val slope = fields.slopeApprox(x, z)
        val slopeBoost = smoothstep(0.7, 2.4, slope)
        return (0.65 * mask + 0.35 * slopeBoost).coerceIn(0.0, 1.0)
      }

      override fun height(ctx: Ctx, x: Int, z: Int): Double {
        val base = fields.globalBaseHeight(x, z)
        val (wx, wz) = noise.warp2("mountWarp", x, z, freq = 0.01, strength = 160.0)
        val xx = wx.toInt()
        val zz = wz.toInt()

        val broad = noise.fbm2("mountBroad", xx, zz, freq = 0.0008, octaves = 4)
        val ridge = noise.ridged2("mountRidge", xx, zz, freq = 0.003, octaves = 4)
        val peaks = ridge * 70.0 + broad * 35.0

        // light terracing for plateaus
        val terrace = terraced(peaks, step = 9.0, sharpness = 0.55)
        return base + terrace
      }

      override fun detail(ctx: Ctx, x: Int, y: Int, z: Int): Double {
        val d = noise.fbm3("mountD", x, y, z, freq = 0.014, octaves = 3)
        return d * 0.18
      }
    }
  }

  */
/**
   * Underground 3D biome influence:
   * - Contributes extra cavern carving + spike additives.
   * - Has high wetness, so it tends to form aquifer lakes even when not sea-connected.
   *//*

  class SpikyCavernsBiome(private val noise: CruxNoiseBank, private val fields: Fields) : Biome {
    override val id = "spiky_caverns"
    override val isSurfaceBiome = false

    override val hydrology = HydrologyProfile(wetness = 0.85, floodsFromSea = true)

    override val materials: MaterialProfile = NoSurface()
    override val caves = null

    override val terrain: TerrainProfile = object : TerrainProfile {
      override fun suitability2D(ctx: Ctx, x: Int, z: Int): Double {
        val m = noise.fbm2("spikeCaveMask", x, z, freq = 0.05, octaves = 2) * 0.5 + 0.5
        return (0.2 + 0.8 * m).coerceIn(0.0, 1.0)
      }

      override fun suitability3D(ctx: Ctx, x: Int, y: Int, z: Int): Double {
        // Strong underground; fades out near/above sea level.
        val t = ((ctx.settings.seaLevel - 28) - y).toDouble() / 45.0
        return smoothstep(0.0, 1.0, t.coerceIn(0.0, 1.3))
      }

      override fun height(ctx: Ctx, x: Int, z: Int): Double {
        // Doesn't change surface height; it’s an underground influence.
        return fields.globalBaseHeight(x, z)
      }

      override fun carve(ctx: Ctx, x: Int, y: Int, z: Int): Double {
        val n = noise.fbm3("spikyCavern", x, y, z, freq = 0.018, octaves = 3)
        val a = abs(n)
        val band = 0.32 // wider => larger caverns
        val carve = 1.0 - smoothstep(band, band + 0.12, a)
        return carve * 1.7
      }

      override fun add(ctx: Ctx, x: Int, y: Int, z: Int): Double {
        // Spikes: add solids back inside open cavern regions
        val open = 1.0 - abs(noise.fbm3("spikeOpen", x, y, z, freq = 0.018, octaves = 2))
        val openMask = smoothstep(0.45, 0.75, open)

        val s = noise.fbm3("spikeField", x, y, z, freq = 0.06, octaves = 2)
        val spike = smoothstep(0.55, 0.85, abs(s))

        // bias spikes near floor/ceiling bands
        val floorBand = smoothstep(0.0, 1.0, ((y - (ctx.minY + 8)).toDouble() / 24.0).coerceIn(0.0, 1.0))
        val ceilingBand = smoothstep(0.0, 1.0, (((ctx.maxY - 12) - y).toDouble() / 24.0).coerceIn(0.0, 1.0))
        val vertical = max(1.0 - floorBand, 1.0 - ceilingBand)

        return spike * openMask * vertical * 0.9
      }
    }
  }

  */
/* ============================================================
   * Resolver + Weights
   * ============================================================ *//*


  data class WeightedBiome(val biome: Biome, val w: Double)

  data class BiomeWeights(val list: List<WeightedBiome>) {
    fun blendedHeight(ctx: Ctx, x: Int, z: Int): Double = list.sumOf { it.biome.terrain.height(ctx, x, z) * it.w }
    fun blendedDetail(ctx: Ctx, x: Int, y: Int, z: Int): Double = list.sumOf { it.biome.terrain.detail(ctx, x, y, z) * it.w }
    fun blendedCarve(ctx: Ctx, x: Int, y: Int, z: Int): Double = list.sumOf { it.biome.terrain.carve(ctx, x, y, z) * it.w }
    fun blendedAdd(ctx: Ctx, x: Int, y: Int, z: Int): Double = list.sumOf { it.biome.terrain.add(ctx, x, y, z) * it.w }

    fun blendedWetness(): Double = list.sumOf { it.biome.hydrology.wetness * it.w }.coerceIn(0.0, 1.0)

    fun dominantSurfaceBiome(): Biome {
      // Choose among surface biomes only (so underground biomes don’t paint grass)
      val surface = list.filter { it.biome.isSurfaceBiome }
      return (surface.maxByOrNull { it.w } ?: list.maxBy { it.w }).biome
    }

    companion object { val EMPTY = BiomeWeights(emptyList()) }
  }

  interface BiomeResolver {
    fun weights2D(ctx: Ctx, x: Int, z: Int): BiomeWeights
    fun weights3D(ctx: Ctx, base2D: BiomeWeights, x: Int, y: Int, z: Int): BiomeWeights
  }

  class DefaultResolver(
    private val fields: Fields,
    private val allBiomes: List<Biome>
  ) : BiomeResolver {

    private val surfaceBiomes = allBiomes.filter { it.isSurfaceBiome }
    private val undergroundBiomes = allBiomes.filter { !it.isSurfaceBiome }

    override fun weights2D(ctx: Ctx, x: Int, z: Int): BiomeWeights {
      // Softmax-like normalization over surface biomes only
      val raw = surfaceBiomes.map { b ->
        b to b.terrain.suitability2D(ctx, x, z).coerceIn(0.0, 1.0)
      }
      val normalized = softmaxPow(raw, sharpness = 2.0)
      return BiomeWeights(normalized.map { (b, w) -> WeightedBiome(b, w) })
    }

    override fun weights3D(ctx: Ctx, base2D: BiomeWeights, x: Int, y: Int, z: Int): BiomeWeights {
      if (undergroundBiomes.isEmpty()) return base2D

      val raw3 = undergroundBiomes.map { b ->
        val s2 = b.terrain.suitability2D(ctx, x, z).coerceIn(0.0, 1.0)
        val s3 = b.terrain.suitability3D(ctx, x, y, z).coerceIn(0.0, 1.0)
        b to (s2 * s3)
      }

      // Normalize 3D layer; then steal up to 35% influence underground
      val layer = softmaxPow(raw3, sharpness = 2.0)
      val layerTotal = layer.sumOf { it.second }.coerceIn(0.0, 1.0)
      val steal = (layerTotal * 0.35).coerceIn(0.0, 0.35)

      val combined = ArrayList<WeightedBiome>(base2D.list.size + layer.size)
      for (wb in base2D.list) combined += WeightedBiome(wb.biome, wb.w * (1.0 - steal))
      for ((b, w) in layer) combined += WeightedBiome(b, w * steal)

      // Renormalize to sum=1
      val sum = combined.sumOf { it.w }.coerceAtLeast(1e-9)
      return BiomeWeights(combined.map { WeightedBiome(it.biome, it.w / sum) })
    }

    private fun softmaxPow(raw: List<Pair<Biome, Double>>, sharpness: Double): List<Pair<Biome, Double>> {
      var sum = 0.0
      val powed = raw.map { (b, s) ->
        val p = s.pow(sharpness)
        sum += p
        b to p
      }
      if (sum <= 1e-9) return listOf(raw.first().first to 1.0)
      return powed.map { (b, p) -> b to (p / sum) }
    }
  }

  */
/* ============================================================
   * Density (terrain + caves)
   * ============================================================ *//*


  class DensityEngine(
    val fields: Fields,
    val noise: CruxNoiseBank,
    val caves: CaveEngine
  ) {
    fun blendedHeight(ctx: Ctx, w: BiomeWeights, x: Int, z: Int): Double {
      // NOTE: Always base on a global field to guarantee continuity
      // (biomes also build off it)
      return w.blendedHeight(ctx, x, z)
    }

    fun densityAt(ctx: Ctx, w: BiomeWeights, height: Double, x: Int, y: Int, z: Int): Double {
      // base heightfield -> density
      var d = (height - y) / ctx.settings.verticalScale

      // detail / overhang roughness
      d += w.blendedDetail(ctx, x, y, z)

      // global caves (subtractive)
      //d -= globalCaves(ctx, height, x, y, z)
      d -= caves.carve(ctx, w, height, x, y, z)

      // biome carvers (subtractive) + additives (spikes etc)
      d -= w.blendedCarve(ctx, x, y, z)
      d += w.blendedAdd(ctx, x, y, z)

      return d
    }

    private fun globalCaves(ctx: Ctx, surfaceY: Double, x: Int, y: Int, z: Int): Double {
      val n = noise.fbm3("globalCaves", x, y, z, freq = 0.028, octaves = 3)
      val a = kotlin.math.abs(n)

      // How far below the terrain surface are we?
      val below = (surfaceY - y.toDouble())

      // Fade caves out near the surface (prevents “messed up land”)
      val surfaceBuffer = 18.0 // blocks: increase to protect surface more
      val surfaceGate = smoothstep(0.0, surfaceBuffer, below).coerceIn(0.0, 1.0)
      // surfaceGate = 0 at/above surface, ramps to 1 when you're ~18 blocks underground

      // Depth ramp (still makes deeper caves more common)
      val top = ctx.settings.seaLevel + 10
      val bottom = ctx.settings.seaLevel - 96
      val depthT = ((top - y).toDouble() / (top - bottom).toDouble()).coerceIn(0.0, 1.0)
      val depth = depthT * depthT

      val band = 0.30 + (0.20 - 0.30) * depth
      val mask = 1.0 - smoothstep(band, band + 0.06, a)

      val minStrength = 1.0
      val maxStrength = 13.0
      val strength = minStrength + (maxStrength - minStrength) * depth

      return mask * strength * surfaceGate
    }



  }

  data class CaveProfile(
    val strengthMin: Double,
    val strengthMax: Double,
    val bandShallow: Double,
    val bandDeep: Double,
    val bandWidth: Double,
    val freq: Double,
    val octaves: Int,
    val surfaceBuffer: Double,   // blocks under surface before caves “turn on”
    val depthTop: Int,           // y where caves start ramping
    val depthBottom: Int         // y where caves are at max
  )


  class CaveEngine(private val noise: CruxNoiseBank) {

    fun carve(ctx: Ctx, w: BiomeWeights, surfaceY: Double, x: Int, y: Int, z: Int): Double {
      var total = 0.0
      for ((biome, bw) in w.list.map { it.biome to it.w }) {
        val p = biome.caves ?: continue
        if (bw <= 0.0001) continue
        total += bw * sample(ctx, p, surfaceY, x, y, z)
      }
      return total
    }

    private fun sample(ctx: Ctx, p: CaveProfile, surfaceY: Double, x: Int, y: Int, z: Int): Double {
      val n = noise.fbm3("caves_${p.freq}_${p.octaves}", x, y, z, p.freq, p.octaves)
      // Convert noise to 0..1
      val tNoise = n * 0.5 + 0.5

      val depthT = ((p.depthTop - y).toDouble() / (p.depthTop - p.depthBottom).toDouble()).coerceIn(0.0, 1.0)
      val depth = depthT * depthT

      val threshold = 0.50 + (0.45 - 0.50) * depth   // much lower
      val mask = smoothstep(threshold, threshold + p.bandWidth, tNoise)


// carve when noise is ABOVE a threshold
//      val threshold = 0.62 + (0.54 - 0.62) * depth   // lower threshold deep => more caves
  //    val mask = smoothstep(threshold, threshold + p.bandWidth, tNoise)


      // depth 0..1

      // “more common deeper”
      //val band = p.bandShallow + (p.bandDeep - p.bandShallow) * depth
      //val mask = 1.0 - smoothstep(band, band + p.bandWidth, DefaultFontInfo.a)

      // “stronger deeper”
      val strength = p.strengthMin + (p.strengthMax - p.strengthMin) * depth

      // protect surface terrain (prevents caves wrecking the landscape)
      val below = surfaceY - y.toDouble()
      val surfaceGate = smoothstep(0.0, p.surfaceBuffer, below).coerceIn(0.0, 1.0)

      return mask * strength * surfaceGate
    }
  }



  */
/* ============================================================
   * Hydrology (aquifers)
   * ============================================================ *//*


  class HydrologyEngine(private val noise: CruxNoiseBank) {
    */
/**
     * Aquifer pockets:
     * - For enclosed caves, decide water by 3D "wetness field" + biome wetness.
     * - Returns true if this air cell should become water (independent of sea connectivity).
     *//*

    fun isAquiferWater(ctx: Ctx, x: Int, y: Int, z: Int, wetness: Double): Boolean {
      if (wetness <= 0.01) return false
      if (y > ctx.settings.seaLevel - 6) return false // keep aquifers mostly underground

      val n = noise.fbm3("aquifer", x, y, z, freq = 0.020, octaves = 3) * 0.5 + 0.5 // 0..1
      // Threshold drops as wetness rises => wetter biomes have more aquifer water
      val threshold = (0.78 - wetness * 0.30).coerceIn(0.35, 0.90)

      // Favor deeper aquifers
      val depthT = ((ctx.settings.seaLevel - 16 - y).toDouble() / 70.0).coerceIn(0.0, 1.0)
      val depthBoost = 0.08 * depthT

      return n > (threshold - depthBoost)
    }
  }

  */
/* ============================================================
   * Materials (surface painting)
   * ============================================================ *//*


  class SimpleSurface(
    private val top: Material,
    private val under: Material,
    private val underDepth: Int
  ) : MaterialProfile {
    override fun paintColumn(
      ctx: Ctx,
      wx: Int,
      wz: Int,
      topY: Int,
      seaLevel: Int,
      get: (Int) -> Material,
      set: (Int, Material) -> Unit
    ) {
      set(topY, top)
      for (i in 1..underDepth) {
        val y = topY - i
        if (y < ctx.minY) break
        val m = get(y)
        if (m == Material.AIR || m == ctx.settings.water) break
        set(y, under)
      }
    }
  }

  class MountainSurface(
    private val dirtDepth: Int,
    private val snowLine: Int
  ) : MaterialProfile {
    override fun paintColumn(
      ctx: Ctx,
      wx: Int,
      wz: Int,
      topY: Int,
      seaLevel: Int,
      get: (Int) -> Material,
      set: (Int, Material) -> Unit
    ) {
      if (topY >= snowLine) {
        set(topY, Material.SNOW_BLOCK)
        for (i in 1..dirtDepth) {
          val y = topY - i
          if (y < ctx.minY) break
          val m = get(y)
          if (m == Material.AIR || m == ctx.settings.water) break
          set(y, Material.RED_CONCRETE)
        }
        return
      }

      // rocky cap with dirt under
      set(topY, Material.RED_CONCRETE)
      for (i in 1..dirtDepth) {
        val y = topY - i
        if (y < ctx.minY) break
        val m = get(y)
        if (m == Material.AIR || m == ctx.settings.water) break
        set(y, Material.RED_TERRACOTTA)
      }
    }
  }

  class NoSurface : MaterialProfile {
    override fun paintColumn(
      ctx: Ctx,
      wx: Int,
      wz: Int,
      topY: Int,
      seaLevel: Int,
      get: (Int) -> Material,
      set: (Int, Material) -> Unit
    ) = Unit
  }

  */
/* ============================================================
   * Math helpers
   * ============================================================ *//*


}
fun terraced(h: Double, step: Double, sharpness: Double): Double {
  if (step <= 0.0) return h
  val q = h / step
  val f = q - floor(q)
  val s = smoothstep(0.5 - sharpness * 0.5, 0.5 + sharpness * 0.5, f)
  val snapped = floor(q) + s
  return snapped * step
}

fun smoothstep(edge0: Double, edge1: Double, x: Double): Double {
  val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
  return t * t * (3 - 2 * t)
}
*/

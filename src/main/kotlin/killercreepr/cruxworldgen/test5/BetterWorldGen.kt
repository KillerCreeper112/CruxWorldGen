/*
package killercreepr.cruxworldgen.test5

import org.bukkit.Material
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.WorldInfo
import java.util.Random
import kotlin.math.*

*/
/**
 * BetterWorldGen: modular, seamless, biome-weighted density field.
 *
 * Architecture:
 *  - Fields: global signals (continents, base height, etc.)
 *  - Resolver: returns biome weights (2D + optional 3D)
 *  - Density: base + biome shape/detail - cave carve + additive solids
 *  - Surface: 2nd pass paints top blocks after solidity is decided
 *//*

class BetterWorldGen(
  private val settings: WorldGenSettings = WorldGenSettings(),
  private val worldGraphFactory: (Long, WorldGenSettings) -> WorldGraph = { seed, s -> WorldGraph.default(seed, s) }
) : ChunkGenerator() {

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

    val g = graph ?: worldGraphFactory(seed, settings).also { graph = it }

    val ctx = GenCtx(
      seed = seed,
      settings = settings,
      chunkX = chunkX,
      chunkZ = chunkZ,
      minY = minY,
      maxY = maxY
    )

    val width = settings.chunkWidth
    require(width == 16) {
      "Minecraft ChunkData expects x,z in 0..15. Set settings.chunkWidth=16."
    }

    // PASS 1: Fill solidity (stone / water / air)
    for (lx in 0 until width) {
      val wx = chunkX * width + lx
      for (lz in 0 until width) {
        val wz = chunkZ * width + lz

        // Precompute 2D weights once per column (fast & seamless)
        val w2 = g.resolver.weights2D(ctx, wx, wz)

        // Optional: column-level precomputed terrain height (blended)
        val terrainHeight = g.density.blendedHeight(ctx, w2, wx, wz)

        for (y in maxY downTo minY) {
          val w3 = g.resolver.weights3D(ctx, w2, wx, y, wz) // defaults to w2 unless you enable 3D biomes
          val d = g.density.densityAt(ctx, w3, terrainHeight, wx, y, wz)

          val mat = when {
            d > 0.0 -> settings.baseStone
            y <= settings.seaLevel -> settings.water
            else -> Material.AIR
          }

          if (mat != Material.AIR) chunkData.setBlock(lx, y, lz, mat)
        }
      }
    }

    // PASS 2: Surface paint (dominant biome decides surface; still seamless overall)
    for (lx in 0 until width) {
      val wx = chunkX * width + lx
      for (lz in 0 until width) {
        val wz = chunkZ * width + lz
        val w2 = g.resolver.weights2D(ctx, wx, wz)

        // find topmost solid (non-air, non-water)
        var topY = Int.MIN_VALUE
        for (y in maxY downTo minY) {
          val m = chunkData.getType(lx, y, lz)
          if (m != Material.AIR && m != settings.water) { topY = y; break }
        }
        if (topY == Int.MIN_VALUE) continue

        val dom = w2.dominant().biome
        dom.surface.paintColumn(
          ctx = ctx,
          wx = wx,
          wz = wz,
          topY = topY,
          seaLevel = settings.seaLevel,
          get = { yy -> chunkData.getType(lx, yy, lz) },
          set = { yy, mat -> chunkData.setBlock(lx, yy, lz, mat) }
        )
      }
    }
  }
}

*/
/* ============================================================
 * Settings / Context
 * ============================================================ *//*


data class WorldGenSettings(
  val chunkWidth: Int = 16,               // keep configurable; must be 16 for ChunkData
  val seaLevel: Int = 64,
  val verticalScale: Double = 10.0,       // larger -> gentler slopes (density changes slower with y)
  val baseStone: Material = Material.STONE,
  val water: Material = Material.WATER
)

data class GenCtx(
  val seed: Long,
  val settings: WorldGenSettings,
  val chunkX: Int,
  val chunkZ: Int,
  val minY: Int,
  val maxY: Int
)

*/
/* ============================================================
 * WorldGraph (composition root)
 * ============================================================ *//*


data class WorldGraph(
  val noise: Noise,
  val fields: Fields,
  val resolver: BiomeResolver,
  val density: DensityEngine
) {
  companion object {
    fun default(seed: Long, settings: WorldGenSettings): WorldGraph {
      val noise = Noise(seed)

      val fields = Fields(
        noise = noise,
        seaLevel = settings.seaLevel
      )

      // Biomes: add/remove freely
      val plains = PlainsBiome(noise, fields)
      val mountains = MountainBiome(noise, fields)

      // 3D cave-biome (underground bias): optional but included
      val spikyCaves = SpikyCavernBiome(noise, fields)

      val biomes2D = listOf(plains, mountains)
      val biomes3D = listOf(spikyCaves) // can be empty; this is “3D influence”

      val resolver = WeightedResolver(
        fields = fields,
        biomes2D = biomes2D,
        biomes3D = biomes3D,
        softmaxSharpness = 2.0          // higher => harder borders; lower => smoother
      )

      val density = DensityEngine(
        fields = fields,
        noise = noise,
        globalCarvers = listOf(BasicCavesCarver(noise, fields)),  // global caves everywhere
      )

      return WorldGraph(noise, fields, resolver, density)
    }
  }
}

*/
/* ============================================================
 * Noise (deterministic, keyed channels)
 * ============================================================ *//*


*/
/**
 * Lightweight gradient-ish value noise w/ fbm, ridged, warp helpers.
 * Outputs are roughly [-1,1]. Good enough for testing architecture.
 *//*

class Noise(private val seed: Long) {

  fun fbm2(key: String, x: Int, z: Int, freq: Double, octaves: Int, lacunarity: Double = 2.0, gain: Double = 0.5): Double {
    var amp = 1.0
    var sum = 0.0
    var norm = 0.0
    var fx = x * freq
    var fz = z * freq
    val salt = hash32(seed, key)

    for (i in 0 until octaves) {
      sum += value2(salt + i * 1013, fx, fz) * amp
      norm += amp
      amp *= gain
      fx *= lacunarity
      fz *= lacunarity
    }
    return (sum / norm).coerceIn(-1.0, 1.0)
  }

  fun fbm3(key: String, x: Int, y: Int, z: Int, freq: Double, octaves: Int, lacunarity: Double = 2.0, gain: Double = 0.5): Double {
    var amp = 1.0
    var sum = 0.0
    var norm = 0.0
    var fx = x * freq
    var fy = y * freq
    var fz = z * freq
    val salt = hash32(seed, key)

    for (i in 0 until octaves) {
      sum += value3(salt + i * 1013, fx, fy, fz) * amp
      norm += amp
      amp *= gain
      fx *= lacunarity
      fy *= lacunarity
      fz *= lacunarity
    }
    return (sum / norm).coerceIn(-1.0, 1.0)
  }

  fun ridged2(key: String, x: Int, z: Int, freq: Double, octaves: Int): Double {
    val n = fbm2(key, x, z, freq, octaves)
    val r = 1.0 - abs(n)             // [0..1]
    return (r * r).coerceIn(0.0, 1.0)
  }

  fun warp2(key: String, x: Int, z: Int, freq: Double, strength: Double): Pair<Double, Double> {
    val dx = fbm2("${key}_dx", x, z, freq, 2) * strength
    val dz = fbm2("${key}_dz", x, z, freq, 2) * strength
    return x + dx to z + dz
  }

  */
/* ----- value noise internals ----- *//*


  private fun value2(s: Int, x: Double, z: Double): Double {
    val x0 = floor(x).toInt()
    val z0 = floor(z).toInt()
    val x1 = x0 + 1
    val z1 = z0 + 1
    val tx = fade(x - x0)
    val tz = fade(z - z0)

    val a = hash2(s, x0, z0)
    val b = hash2(s, x1, z0)
    val c = hash2(s, x0, z1)
    val d = hash2(s, x1, z1)

    val ab = lerp(a, b, tx)
    val cd = lerp(c, d, tx)
    return lerp(ab, cd, tz)
  }

  private fun value3(s: Int, x: Double, y: Double, z: Double): Double {
    val x0 = floor(x).toInt()
    val y0 = floor(y).toInt()
    val z0 = floor(z).toInt()
    val x1 = x0 + 1
    val y1 = y0 + 1
    val z1 = z0 + 1

    val tx = fade(x - x0)
    val ty = fade(y - y0)
    val tz = fade(z - z0)

    fun h(ix: Int, iy: Int, iz: Int) = hash3(s, ix, iy, iz)

    val c000 = h(x0, y0, z0)
    val c100 = h(x1, y0, z0)
    val c010 = h(x0, y1, z0)
    val c110 = h(x1, y1, z0)

    val c001 = h(x0, y0, z1)
    val c101 = h(x1, y0, z1)
    val c011 = h(x0, y1, z1)
    val c111 = h(x1, y1, z1)

    val x00 = lerp(c000, c100, tx)
    val x10 = lerp(c010, c110, tx)
    val x01 = lerp(c001, c101, tx)
    val x11 = lerp(c011, c111, tx)

    val y0v = lerp(x00, x10, ty)
    val y1v = lerp(x01, x11, ty)
    return lerp(y0v, y1v, tz)
  }

  private fun fade(t: Double): Double = t * t * t * (t * (t * 6 - 15) + 10)
  private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

  private fun hash2(s: Int, x: Int, z: Int): Double {
    val h = mix32(s xor (x * 374761393) xor (z * 668265263))
    return ((h ushr 8) / 16777216.0) * 2.0 - 1.0
  }

  private fun hash3(s: Int, x: Int, y: Int, z: Int): Double {
    val h = mix32(s xor (x * 374761393) xor (y * 1442695041) xor (z * 668265263))
    return ((h ushr 8) / 16777216.0) * 2.0 - 1.0
  }

  private fun hash32(seed: Long, key: String): Int {
    var h = (seed xor (seed ushr 32)).toInt()
    for (c in key) h = mix32(h xor c.code)
    return h
  }

  private fun mix32(x: Int): Int {
    var v = x
    v = v xor (v ushr 16)
    v *= -2048144789
    v = v xor (v ushr 13)
    v *= -1028477387
    v = v xor (v ushr 16)
    return v
  }
}

*/
/* ============================================================
 * Global fields (continents, base height, etc.)
 * ============================================================ *//*


class Fields(
  private val noise: Noise,
  private val seaLevel: Int
) {

  */
/**
   * Continentalness: big land/ocean mask ~[-1,1]
   *//*

  fun continental(x: Int, z: Int): Double {
    val (wx, wz) = noise.warp2("contWarp", x, z, freq = 0.0005, strength = 120.0)
    return noise.fbm2("continental", wx.toInt(), wz.toInt(), freq = 0.00025, octaves = 4)
  }

  */
/**
   * Base land height (not biome-specific): gives global continuity.
   *//*

  fun globalBaseHeight(x: Int, z: Int): Double {
    val c = continental(x, z) // [-1,1]
    val land = smoothstep(-0.15, 0.25, c) // 0..1 (ocean->land)
    val broad = noise.fbm2("broadHeight", x, z, freq = 0.0006, octaves = 3) // [-1,1]
    val hills = noise.fbm2("broadHills", x, z, freq = 0.0012, octaves = 3) // [-1,1]
    val height = seaLevel + land * (18.0 + broad * 14.0 + hills * 10.0)
    return height
  }

  fun slopeApprox(x: Int, z: Int): Double {
    // quick slope estimate from base height field
    val h = globalBaseHeight(x, z)
    val hx = globalBaseHeight(x + 8, z)
    val hz = globalBaseHeight(x, z + 8)
    return max(abs(hx - h), abs(hz - h)) / 8.0
  }
}

*/
/* ============================================================
 * Biomes
 * ============================================================ *//*


data class WeightedBiome(val biome: Biome, val w: Double)

interface Biome {
  val id: String
  val surface: SurfacePainter

  */
/**
   * Suitability in [0..1]. Resolver will normalize/softmax these.
   * This is 2D suitability (x,z). 3D biomes have an extra 3D suitability.
   *//*

  fun suitability2D(ctx: GenCtx, x: Int, z: Int): Double = 0.0

  */
/**
   * Optional 3D suitability multiplier in [0..1] based on y.
   * Default = 1 (pure 2D biome).
   *//*

  fun suitability3D(ctx: GenCtx, x: Int, y: Int, z: Int): Double = 1.0

  */
/**
   * Biome contributes a preferred height at (x,z).
   * It should generally build off fields.globalBaseHeight for continuity.
   *//*

  fun height(ctx: GenCtx, x: Int, z: Int): Double

  */
/**
   * Small density detail (overhang roughness, etc.)
   *//*

  fun densityDetail(ctx: GenCtx, x: Int, y: Int, z: Int): Double

  */
/**
   * Biome-specific carvers and additives (optional).
   *//*

  val localCarvers: List<Carver> get() = emptyList()
  val localAdditives: List<Additive> get() = emptyList()
}

interface SurfacePainter {
  fun paintColumn(
    ctx: GenCtx,
    wx: Int,
    wz: Int,
    topY: Int,
    seaLevel: Int,
    get: (Int) -> Material,
    set: (Int, Material) -> Unit
  )
}

interface Carver {
  */
/** returns carve amount to subtract from density (>=0) *//*

  fun carve(ctx: GenCtx, x: Int, y: Int, z: Int, w: Double): Double
}

interface Additive {
  */
/** returns additive solid amount to add to density (can be 0..+) *//*

  fun add(ctx: GenCtx, x: Int, y: Int, z: Int, w: Double): Double
}

*/
/* ---------- Plains: tame / flat ---------- *//*


class PlainsBiome(private val noise: Noise, private val fields: Fields) : Biome {
  override val id: String = "plains"

  override val surface: SurfacePainter = SimpleSurface(
    top = Material.GRASS_BLOCK,
    under = Material.DIRT,
    underDepth = 4
  )

  override fun suitability2D(ctx: GenCtx, x: Int, z: Int): Double {
    // Plains prefer lower slope and lower “mount mask”
    val slope = fields.slopeApprox(x, z) // ~0..?
    val mask = noise.fbm2("biomeMask", x, z, freq = 0.0007, octaves = 3) * 0.5 + 0.5 // 0..1
    val slopePref = (1.0 - smoothstep(0.9, 2.2, slope)).coerceIn(0.0, 1.0)
    val maskPref = (1.0 - mask).coerceIn(0.0, 1.0)
    return (0.55 * slopePref + 0.45 * maskPref).coerceIn(0.0, 1.0)
  }

  override fun height(ctx: GenCtx, x: Int, z: Int): Double {
    val base = fields.globalBaseHeight(x, z)
    val gentle = noise.fbm2("plainsH", x, z, freq = 0.0014, octaves = 3) // [-1,1]
    return base + gentle * 6.0
  }

  override fun densityDetail(ctx: GenCtx, x: Int, y: Int, z: Int): Double {
    val d = noise.fbm3("plainsD", x, y, z, freq = 0.02, octaves = 2)
    return d * 0.07
  }
}

*/
/* ---------- Mountains: huge / dramatic ---------- *//*


class MountainBiome(private val noise: Noise, private val fields: Fields) : Biome {
  override val id: String = "mountains"

  override val surface: SurfacePainter = MountainSurface(
    dirtDepth = 3,
    snowLine = 130
  )

  override fun suitability2D(ctx: GenCtx, x: Int, z: Int): Double {
    val mask = noise.fbm2("biomeMask", x, z, freq = 0.0007, octaves = 3) * 0.5 + 0.5 // 0..1
    val slope = fields.slopeApprox(x, z)
    val slopeBoost = smoothstep(0.7, 2.4, slope) // 0..1
    return (0.65 * mask + 0.35 * slopeBoost).coerceIn(0.0, 1.0)
  }

  override fun height(ctx: GenCtx, x: Int, z: Int): Double {
    val base = fields.globalBaseHeight(x, z)

    // Domain warp for dramatic mountain ranges (prevents boring blobs)
    val (wx, wz) = noise.warp2("mountWarp", x, z, freq = 0.0010, strength = 160.0)
    val xx = wx.toInt()
    val zz = wz.toInt()

    val broad = noise.fbm2("mountBroad", xx, zz, freq = 0.0008, octaves = 4)      // [-1,1]
    val ridge = noise.ridged2("mountRidge", xx, zz, freq = 0.0018, octaves = 4)   // [0,1]
    val peaks = ridge * 70.0 + broad * 35.0

    // Add plateaus/terracing a little (optional vibe)
    val terrace = terraced(peaks, step = 9.0, sharpness = 0.55)

    return base + terrace
  }

  override fun densityDetail(ctx: GenCtx, x: Int, y: Int, z: Int): Double {
    // more roughness -> overhang character
    val d = noise.fbm3("mountD", x, y, z, freq = 0.014, octaves = 3)
    return d * 0.18
  }
}

*/
/* ---------- 3D Cave-biome: spiky caverns underground ---------- *//*


class SpikyCavernBiome(private val noise: Noise, private val fields: Fields) : Biome {
  override val id: String = "spiky_caverns"

  override val surface: SurfacePainter = NoSurface()

  override fun suitability2D(ctx: GenCtx, x: Int, z: Int): Double {
    // faint presence everywhere; mostly controlled by suitability3D (underground)
    val m = noise.fbm2("spikeCaveMask", x, z, freq = 0.0010, octaves = 2) * 0.5 + 0.5
    return (0.2 + 0.8 * m).coerceIn(0.0, 1.0)
  }

  override fun suitability3D(ctx: GenCtx, x: Int, y: Int, z: Int): Double {
    // Only “exists” underground. Strongly fades out near/above sea level.
    val t = ((ctx.settings.seaLevel - 8) - y).toDouble() / 55.0
    return smoothstep(0.0, 1.0, t.coerceIn(0.0, 1.2))
  }

  override fun height(ctx: GenCtx, x: Int, z: Int): Double {
    // This biome does not change surface height; it influences underground via carvers/additives.
    return fields.globalBaseHeight(x, z)
  }

  override fun densityDetail(ctx: GenCtx, x: Int, y: Int, z: Int): Double = 0.0

  override val localCarvers: List<Carver> = listOf(
    CavernCarver(noise)
  )

  override val localAdditives: List<Additive> = listOf(
    SpikeAdditive(noise)
  )
}

*/
/* ============================================================
 * Resolver: weights (2D + 3D)
 * ============================================================ *//*


interface BiomeResolver {
  fun weights2D(ctx: GenCtx, x: Int, z: Int): BiomeWeights
  fun weights3D(ctx: GenCtx, base2D: BiomeWeights, x: Int, y: Int, z: Int): BiomeWeights
}

data class BiomeWeights(val list: List<WeightedBiome>) {
  fun dominant(): WeightedBiome = list.maxBy { it.w }
}

*/
/**
 * WeightedResolver:
 * - computes suitability for each 2D biome
 * - uses softmax-ish normalization for smooth blending
 * - then applies 3D biomes as additional influences underground (optional)
 *//*

class WeightedResolver(
  private val fields: Fields,
  private val biomes2D: List<Biome>,
  private val biomes3D: List<Biome>,
  private val softmaxSharpness: Double = 2.0
) : BiomeResolver {

  override fun weights2D(ctx: GenCtx, x: Int, z: Int): BiomeWeights {
    val raw = ArrayList<Pair<Biome, Double>>(biomes2D.size)
    for (b in biomes2D) raw += b to b.suitability2D(ctx, x, z).coerceIn(0.0, 1.0)

    val w = softmaxNormalize(raw, sharpness = softmaxSharpness)
    return BiomeWeights(w)
  }

  override fun weights3D(ctx: GenCtx, base2D: BiomeWeights, x: Int, y: Int, z: Int): BiomeWeights {
    if (biomes3D.isEmpty()) return base2D

    // Compute 3D biome weights as a "layer" that steals some influence underground.
    val layerRaw = ArrayList<Pair<Biome, Double>>(biomes3D.size)
    for (b in biomes3D) {
      val s2 = b.suitability2D(ctx, x, z).coerceIn(0.0, 1.0)
      val s3 = b.suitability3D(ctx, x, y, z).coerceIn(0.0, 1.0)
      layerRaw += b to (s2 * s3)
    }

    val layer = softmaxNormalize(layerRaw, sharpness = 2.0)
    val layerTotal = layer.sumOf { it.w }.coerceIn(0.0, 1.0)

    // Blend: keep most of base2D, but allow 3D biome(s) to take up to ~35% influence underground.
    val steal = (layerTotal * 0.35).coerceIn(0.0, 0.35)

    val combined = ArrayList<WeightedBiome>()
    for (wb in base2D.list) combined += WeightedBiome(wb.biome, wb.w * (1.0 - steal))
    for (wb in layer) combined += WeightedBiome(wb.biome, wb.w * steal)

    // Renormalize
    val sum = combined.sumOf { it.w }.coerceAtLeast(1e-9)
    return BiomeWeights(combined.map { WeightedBiome(it.biome, it.w / sum) })
  }

  private fun softmaxNormalize(raw: List<Pair<Biome, Double>>, sharpness: Double): List<WeightedBiome> {
    // softmax-ish: w_i = s_i^k / sum(s^k) with k>1
    var sum = 0.0
    val powed = raw.map { (b, s) ->
      val p = s.pow(sharpness)
      sum += p
      b to p
    }
    if (sum <= 1e-9) return listOf(WeightedBiome(raw.first().first, 1.0))
    return powed.map { (b, p) -> WeightedBiome(b, p / sum) }
  }
}

*/
/* ============================================================
 * Density Engine: base + biome + caves + additives
 * ============================================================ *//*


class DensityEngine(
  private val fields: Fields,
  private val noise: Noise,
  private val globalCarvers: List<GlobalCarver>
) {

  fun blendedHeight(ctx: GenCtx, w: BiomeWeights, x: Int, z: Int): Double {
    var h = 0.0
    for (wb in w.list) h += wb.biome.height(ctx, x, z) * wb.w
    return h
  }

  fun densityAt(ctx: GenCtx, w: BiomeWeights, terrainHeight: Double, x: Int, y: Int, z: Int): Double {
    // Base surface density: heightfield -> density
    var density = (terrainHeight - y) / ctx.settings.verticalScale

    // Biome 3D detail (overhang roughness)
    var detail = 0.0
    for (wb in w.list) detail += wb.biome.densityDetail(ctx, x, y, z) * wb.w
    density += detail

    // Global caves
    var carve = 0.0
    for (gc in globalCarvers) carve += gc.carve(ctx, x, y, z, w)
    density -= carve

    // Biome-local carvers (e.g. spiky cavern biome)
    for (wb in w.list) {
      for (c in wb.biome.localCarvers) density -= c.carve(ctx, x, y, z, wb.w)
    }

    // Biome-local additives (spikes/pillars that re-add solids inside caves)
    var add = 0.0
    for (wb in w.list) {
      for (a in wb.biome.localAdditives) add += a.add(ctx, x, y, z, wb.w)
    }
    density += add

    return density
  }
}

interface GlobalCarver {
  fun carve(ctx: GenCtx, x: Int, y: Int, z: Int, weights: BiomeWeights): Double
}

*/
/**
 * Global caves everywhere: adjustable via frequency/band.
 * This is your "something that generates caves" baseline.
 *//*

class BasicCavesCarver(private val noise: Noise, private val fields: Fields) : GlobalCarver {
  override fun carve(ctx: GenCtx, x: Int, y: Int, z: Int, weights: BiomeWeights): Double {
    val ceiling = ctx.settings.seaLevel - 12
    if (y > ceiling) return 0.0

    val n = noise.fbm3("globalCaves", x, y, z, freq = 0.035, octaves = 3)
    val a = kotlin.math.abs(n)

    val band = 0.20
    val carve = 1.0 - smoothstep(band, band + 0.10, a)

    val depthT = ((ceiling - y).toDouble() / 45.0).coerceIn(0.0, 1.0)
    val boost = 0.4 + depthT * 1.0

    val strength = 10.0
    return carve * boost * strength
  }
}



*/
/* ============================================================
 * Spiky cavern local behaviors
 * ============================================================ *//*


class CavernCarver(private val noise: Noise) : Carver {
  override fun carve(ctx: GenCtx, x: Int, y: Int, z: Int, w: Double): Double {
    if (w <= 1e-4) return 0.0
    val n = noise.fbm3("spikyCavern", x, y, z, freq = 0.018, octaves = 3)
    val a = abs(n)

    // Wider than global -> big caverns
    val band = 0.32
    val carve = 1.0 - smoothstep(band, band + 0.12, a)
    return carve * 1.7 * w
  }
}

class SpikeAdditive(private val noise: Noise) : Additive {
  override fun add(ctx: GenCtx, x: Int, y: Int, z: Int, w: Double): Double {
    if (w <= 1e-4) return 0.0

    // A mask so spikes only appear in "open cavern" zones:
    val open = 1.0 - abs(noise.fbm3("spikeOpen", x, y, z, freq = 0.018, octaves = 2))
    val openMask = smoothstep(0.45, 0.75, open)

    // Spike field: thin solids distributed sparsely
    val s = noise.fbm3("spikeField", x, y, z, freq = 0.06, octaves = 2) // [-1,1]
    val spike = smoothstep(0.55, 0.85, abs(s))

    // Vertical shaping: stronger near floor/ceiling bands
    val floorBand = smoothstep(0.0, 1.0, ((y - (ctx.minY + 8)).toDouble() / 24.0).coerceIn(0.0, 1.0))
    val ceilingBand = smoothstep(0.0, 1.0, (((ctx.maxY - 12) - y).toDouble() / 24.0).coerceIn(0.0, 1.0))
    val vertical = max(1.0 - floorBand, 1.0 - ceilingBand) // near extremes => 1

    return spike * openMask * vertical * 0.9 * w
  }
}

*/
/* ============================================================
 * Surface painters
 * ============================================================ *//*


class SimpleSurface(
  private val top: Material,
  private val under: Material,
  private val underDepth: Int
) : SurfacePainter {
  override fun paintColumn(ctx: GenCtx, wx: Int, wz: Int, topY: Int, seaLevel: Int, get: (Int) -> Material, set: (Int, Material) -> Unit) {
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
) : SurfacePainter {
  override fun paintColumn(ctx: GenCtx, wx: Int, wz: Int, topY: Int, seaLevel: Int, get: (Int) -> Material, set: (Int, Material) -> Unit) {
    if (topY >= snowLine) {
      set(topY, Material.SNOW_BLOCK)
      for (i in 1..dirtDepth) {
        val y = topY - i
        if (y < ctx.minY) break
        val m = get(y)
        if (m == Material.AIR || m == ctx.settings.water) break
        set(y, Material.STONE)
      }
      return
    }

    // rocky top + dirt underneath
    set(topY, Material.STONE)
    for (i in 1..dirtDepth) {
      val y = topY - i
      if (y < ctx.minY) break
      val m = get(y)
      if (m == Material.AIR || m == ctx.settings.water) break
      set(y, Material.DIRT)
    }
  }
}

class NoSurface : SurfacePainter {
  override fun paintColumn(ctx: GenCtx, wx: Int, wz: Int, topY: Int, seaLevel: Int, get: (Int) -> Material, set: (Int, Material) -> Unit) = Unit
}

*/
/* ============================================================
 * Math helpers
 * ============================================================ *//*


fun smoothstep(edge0: Double, edge1: Double, x: Double): Double {
  val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
  return t * t * (3 - 2 * t)
}

*/
/**
 * Terracing/plateau effect applied to a height delta.
 * step: size of terraces; sharpness 0..1 (higher => sharper plateaus)
 *//*

fun terraced(h: Double, step: Double, sharpness: Double): Double {
  if (step <= 0.0) return h
  val q = h / step
  val f = q - floor(q)
  val s = smoothstep(0.5 - sharpness * 0.5, 0.5 + sharpness * 0.5, f)
  val snapped = floor(q) + s
  return snapped * step
}
*/

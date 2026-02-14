package killercreepr.cruxworldgen.test.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*
import kotlin.math.pow

class MegaCavernRegions(
  private val regionFreq: Double = 0.0012,          // lower => bigger cavern "zones"
  private val regionThreshold01: Double = 0.62,     // higher => rarer zones
  private val regionPower: Double = 2.2,            // higher => sharper zone edges

  private val cavernFreq: Double = 0.010,           // cavern texture inside zones
  private val cavernThreshold01: Double = 0.57,     // lower => more open space
  private val cavernPower: Double = 2.5,//1.8,

  private val baseDepthBelowSurface: Double = 34.0, // where caverns "want" to live
  private val depthVariation: Double = 22.0,
  private val verticalRadius: Double = 46.0,        // thickness of the band

  private val allowOpenToSurfaceDepth: Int = 8,     // allow mouths within 8 blocks below surface
  private val strength: Double = 1.25,
  private val openMarginBlocks: Double = 20.0,//10.0,
  private val warpBlocks: Double = 55.0,
  override val surfaceFadeStart : Int = 0,
  override val surfaceFadeRamp: Int = 0
) : CaveType, Noised {

  object Noise : NoiseModule {
    object Warp3D : NoiseKey { override val id = "cave.megacavern.warp3D" }
    object Region3D : NoiseKey { override val id = "cave.megacavern.region3D" }
    object Cavern3D : NoiseKey { override val id = "cave.megacavern.cavern3D" }
    object Height2D : NoiseKey { override val id = "cave.megacavern.height2D" }
    object Overhang3D : NoiseKey { override val id = "biome.amplified.overhang3D" }


    override fun install(bank: NoiseBank) {
      bank.register(Warp3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.01)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
      bank.register(Region3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0012)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
      bank.register(Cavern3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.010)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(3)
        }
      }
      bank.register(Height2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0020)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
      bank.register(Overhang3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.018) // try 0.012..0.028 (higher = smaller, more frequent overhangs)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
    }
  }

  override val noiseModule = Noise

  override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    val solidDensity = kotlin.math.max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0

    // allow near-surface openings for big mouths
    if (cave.depthBelowSurface < -2) return 0.0
    if (cave.depthBelowSurface <= 0) {
      // only carve above/at surface in very rare cases (keeps sky from swiss-cheese)
      return 0.0
    }
    if (cave.depthBelowSurface > 0 && cave.depthBelowSurface > 2000) return 0.0

    val wx = cave.worldX.toDouble()
    val wy = cave.y.toDouble()
    val wz = cave.worldZ.toDouble()

    // --- Warp domain ---
    val warpN = ctx.noise.get(Noise.Warp3D).noise3D(wx, wy * 0.25, wz)
    val xw = wx + warpN * warpBlocks
    val zw = wz + ctx.noise.get(Noise.Warp3D).noise3D(wx + 1000.0, wy * 0.25, wz + 1000.0) * warpBlocks

    // --- Region gating (0..1) ---
    val region01 = (ctx.noise.get(Noise.Region3D).noise3D(xw, wy * 0.18, zw) + 1.0) * 0.5
    val rT = ((region01 - regionThreshold01) / (1.0 - regionThreshold01)).coerceIn(0.0, 1.0)
    val regionMask = rT * rT * (3.0 - 2.0 * rT) // smoothstep01

    if (regionMask <= 0.001) return 0.0

    val regionSharp = regionMask.pow(regionPower)

    // --- Vertical band (prefers deep-ish but allows mouths) ---
    val heightNoise = ctx.noise.get(Noise.Height2D).noise2D(cave.worldX, cave.worldZ) // [-1..1]
    val targetDepth = baseDepthBelowSurface + heightNoise * depthVariation
    val centerY = cave.surfaceY - targetDepth

    val dy = kotlin.math.abs(wy - centerY)
    var vT = ((verticalRadius - dy) / verticalRadius).coerceIn(0.0, 1.0)
    vT = vT * vT * (3.0 - 2.0 * vT)

    // Mouth boost: near the surface, loosen the vertical constraint so openings happen.
    val mouthT = ((allowOpenToSurfaceDepth - cave.depthBelowSurface).toDouble() / allowOpenToSurfaceDepth)
      .coerceIn(0.0, 1.0)
    val mouthBoost = mouthT * mouthT * (3.0 - 2.0 * mouthT) // 1 when very near surface, 0 deeper
    val verticalMask = kotlin.math.max(vT, mouthBoost * 0.85)

    if (verticalMask <= 0.001) return 0.0

    // --- Cavern texture inside zone (0..1) ---
    val cav01 = (ctx.noise.get(Noise.Cavern3D).noise3D(xw, wy * 0.6, zw) + 1.0) * 0.5
    val cT = ((cav01 - cavernThreshold01) / (1.0 - cavernThreshold01)).coerceIn(0.0, 1.0)
    val cavernMask = (cT * cT * (3.0 - 2.0 * cT)).pow(cavernPower)

    if (cavernMask <= 0.01) return 0.0

    val mask = regionSharp * verticalMask * cavernMask

    // relative carve strength to punch through amplified cliffs
    return mask * (solidDensity * strength + openMarginBlocks)
  }
}

package killercreepr.cruxworldgen.standard.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

class AmplifiedRavines(
  private val corridorRadius: Double = 0.20,        // in noise-space distance (smaller => thinner corridors)
  private val ravineWidthBlocks: Double = 16.0,     // horizontal “radius” of ravine
  private val ravineHeightBlocks: Double = 34.0,    // vertical “radius” (taller = more dramatic)
  private val baseDepthBelowSurface: Double = 22.0, // ravines tend to be closer to surface than spaghetti
  private val depthVariation: Double = 30.0,
  private val allowOpenToSurfaceDepth: Int = 30,//10,    // allow mouths
  private val strength: Double = 10.0,//1.35,
  private val openMarginBlocks: Double = 20.0,//10.0,
  private val warpBlocks: Double = 95.0,
  override val surfaceFadeStart : Int = 0,
  override val surfaceFadeRamp: Int = 0
) : CaveType, Noised {

  object Noise : NoiseModule {
    object Warp2D : NoiseKey { override val id = "cave.ravine.warp2D" }
    object Corridors2D : NoiseKey { override val id = "cave.ravine.corridors2D" }
    object Height2D : NoiseKey { override val id = "cave.ravine.height2D" }

    override fun install(bank: NoiseBank) {
      bank.register(Warp2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0009)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
      bank.register(Corridors2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0014)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(1)
        }
      }
      bank.register(Height2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0016)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
    }
  }

  override val noiseModule = Noise

  override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    val solidDensity = max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0
    if (cave.depthBelowSurface <= 0) return 0.0

    val wx = cave.worldX.toDouble()
    val wz = cave.worldZ.toDouble()

    // --- 1) Warp the ravine network so it forms meandering “cuts” ---
    val warpN = ctx.noise.get(Noise.Warp2D)
    val warpX = warpN.noise2D(wx, wz) * warpBlocks
    val warpZ = warpN.noise2D(wx + 1000.0, wz + 1000.0) * warpBlocks
    val xw = wx + warpX
    val zw = wz + warpZ

    // --- 2) Corridor field in XZ ---
    // Think of abs(noise) as "distance to a corridor axis" (not perfect, but works visually).
    val c = ctx.noise.get(Noise.Corridors2D).noise2D(xw, zw) // [-1..1]
    val axisDist = abs(c)

    // corridor mask (0..1), sharp so it becomes a cut line
    val t = ((corridorRadius - axisDist) / corridorRadius).coerceIn(0.0, 1.0)
    val corridorMask = (t * t * t).pow(1.35)
    if (corridorMask < 0.70) return 0.0

    // --- 3) Vertical profile: pick a center band like your spaghetti ---
    val heightNoise = ctx.noise.get(Noise.Height2D).noise2D(cave.worldX, cave.worldZ) // [-1..1]
    val targetDepth = baseDepthBelowSurface + heightNoise * depthVariation
    val centerY = cave.surfaceY - targetDepth

    val dy = abs(cave.y.toDouble() - centerY)
    val vT = ((ravineHeightBlocks - dy) / ravineHeightBlocks).coerceIn(0.0, 1.0)
    val verticalMask = vT * vT * (3.0 - 2.0 * vT)

    // Near-surface mouth allowance
    /*val mouthT = ((allowOpenToSurfaceDepth - cave.depthBelowSurface).toDouble() / allowOpenToSurfaceDepth)
      .coerceIn(0.0, 1.0)
    val mouthMask = mouthT * mouthT * (3.0 - 2.0 * mouthT)*/

    val v = verticalMask//max(verticalMask, mouthMask * 0.75)
    if (v <= 0.001) return 0.0

    val mask = corridorMask * v

    // Scale carve: “width” is simulated by stronger carve where corridorMask is high
    // (your system will subtract carve from density; strong carve tends to widen results)
    val widthBoost = 0.65 + 0.35 * corridorMask
    return mask * widthBoost * (solidDensity * strength + openMarginBlocks)
  }
}

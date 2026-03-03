package killercreepr.cruxworldgen.standard.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import kotlin.math.max

class SurfaceEntrances(
  val threshold01: Double = 0.80,
  val bandTopDepth: Double = 2.0,
  val bandBottomDepth: Double = 18.0,
  val strength: Double = 1.0,
  val openMarginBlocks: Double = 16.0,

  override val surfaceFadeStart: Int = 0,
  override val surfaceFadeRamp: Int = 0
) : CaveType, Noised {

  object Noise : NoiseModule {
    object Openings2D : NoiseKey { override val id = "cave.surface_entrances.openings_2D" }

    override fun install(bank: NoiseBank) {
      bank.register(Openings2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.018)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
    }
  }

  override val noiseModule = Noise

  override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    val solidDensity = max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0
    //if (cave.depthBelowSurface < 0) return 0.0

    val depth = cave.depthBelowSurface
    if (depth < bandTopDepth || depth > bandBottomDepth) return 0.0

    val n01 = (ctx.noise.get(Noise.Openings2D).noise2D(cave.worldX, cave.worldZ) + 1.0) * 0.5
    val openMask = ((n01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    if (openMask <= 0.0) return 0.0

    val bandT = ((depth - bandTopDepth) / (bandBottomDepth - bandTopDepth)).coerceIn(0.0, 1.0)
    val depthMask = smoothstep01(bandT) * (1.0 - smoothstep01(((depth - 10.0) / (bandBottomDepth - 10.0)).coerceIn(0.0, 1.0)))

    val mask = smoothstep01(openMask) * depthMask
    if (mask <= 0.001) return 0.0

    return mask * (solidDensity * strength + openMarginBlocks)
  }
}
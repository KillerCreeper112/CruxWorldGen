package killercreepr.cruxworldgen.standard.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import kotlin.math.abs
import kotlin.math.max

class VerticalRifts(
  val ridgeWidth01: Double = 0.12,
  val strength: Double = 1.08,
  val openMarginBlocks: Double = 12.0,

  val minDepthBelowSurface: Double = 8.0,
  val fullDepthBelowSurface: Double = 25.0,

  override val surfaceFadeStart: Int = 0,
  override val surfaceFadeRamp: Int = 0
) : CaveType, Noised {

  object Noise : NoiseModule {
    object Rift2D : NoiseKey { override val id = "cave.vertical_rift.rift_2D" }

    override fun install(bank: NoiseBank) {
      bank.register(Rift2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.008)
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
    if (cave.depthBelowSurface < 0) return 0.0

    val depth = cave.depthBelowSurface
    if (depth < minDepthBelowSurface) return 0.0

    val n = abs(ctx.noise.get(Noise.Rift2D).noise2D(cave.worldX, cave.worldZ))
    val horizontalMask = 1.0 - smoothstep01((n / ridgeWidth01).coerceIn(0.0, 1.0))
    if (horizontalMask <= 0.001) return 0.0

    val depthMask = ((depth - minDepthBelowSurface) / (fullDepthBelowSurface - minDepthBelowSurface)).coerceIn(0.0, 1.0)
    val mask = horizontalMask * smoothstep01(depthMask)
    if (mask <= 0.001) return 0.0

    return mask * (solidDensity * strength + openMarginBlocks)
  }
}
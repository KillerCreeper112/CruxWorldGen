package killercreepr.cruxworldgen.standard.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.NoiseBank
import killercreepr.cruxworldgen.api.noise.NoiseField
import killercreepr.cruxworldgen.api.noise.NoiseKey
import killercreepr.cruxworldgen.api.noise.NoiseModule
import killercreepr.cruxworldgen.api.noise.Noised
import killercreepr.cruxworldgen.api.util.Curve
import killercreepr.cruxworldgen.core.feature.GenerateHeightSampler
import kotlin.math.abs
import kotlin.math.max

class LavaTubes(
  val noodleRadius: Double = 0.5,
  val verticalRadiusBlocks: Double = 6.0,

  val baseDepthBelowSurface: Double = 75.0,
  val depthVariationBlocks: Double = 10.0,

  val strength: Double = 1.12,
  val openMarginBlocks: Double = 10.0,
  val warpBlocks: Double = 18.0,
  val halfWidthBlocks: Double = 48.0,
  val centerYBlocks: GenerateHeightSampler = GenerateHeightSampler.Companion.relative(0.5),
  override val surfaceFadeStart : Int = 4,
  override val surfaceFadeRamp: Int = 8
) : CaveType, Noised {

  object Noise : NoiseModule {
    object Warp3D : NoiseKey { override val id = "cave.lava_tubes.warp3D" }
    object Worm3D : NoiseKey { override val id = "cave.lava_tubes.worm3D" }
    object Height2D : NoiseKey { override val id = "cave.lava_tubes.height2D" }

    override fun install(bank: NoiseBank) {
      bank.register(Warp3D){ seed ->
        NoiseField.Companion.noiseField(seed){
          frequency(0.018)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
      bank.register(Worm3D){ seed ->
        NoiseField.Companion.noiseField(seed){
          frequency(0.008)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
      bank.register(Height2D){ seed ->
        NoiseField.Companion.noiseField(seed){
          frequency(0.0025)
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
    //if (cave.depthBelowSurface <= 0) return 0.0

    // centerline depth slowly varies across XZ
    //todo val hNoise = ctx.noise.get(Noise.Height2D).noise2D(cave.worldX, cave.worldZ) // [-1..1]
    //todo val centerY = cave.surfaceY - (baseDepthBelowSurface + hNoise * depthVariationBlocks)

    val dy = abs(cave.y.toDouble() - centerYBlocks.sampleY(ctx))
    val bandT = ((halfWidthBlocks - dy) / halfWidthBlocks).coerceIn(0.0, 1.0)
    val verticalMask = Curve.smoothstep01(bandT)
    if (verticalMask <= 0.001) return 0.0

    val warp = ctx.noise.get(Noise.Warp3D).noise3D(cave.worldX, 0, cave.worldZ)
    val wx = (cave.worldX + warp * warpBlocks).toInt()
    val wz = (cave.worldZ + warp * warpBlocks).toInt()

    val worm = ctx.noise.get(Noise.Worm3D).noise3D(wx, 0, wz)
    val axisDist = abs(worm)

    val t = ((noodleRadius - axisDist) / noodleRadius).coerceIn(0.0, 1.0)
    val noodleMask = t * t * t
    if (noodleMask < 0.50) return 0.0

    val mask = noodleMask * verticalMask
    return mask * (solidDensity * strength + openMarginBlocks)
  }
}
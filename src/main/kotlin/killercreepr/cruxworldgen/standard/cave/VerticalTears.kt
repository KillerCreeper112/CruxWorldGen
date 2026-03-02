package killercreepr.cruxworldgen.standard.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import killercreepr.cruxworldgen.core.feature.GenerateHeightSampler
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

class VerticalTears(
  val slitRadius: Double = 0.22,               // thinner = sharper tears
  val slitStrength: Double = 1.16,
  val openMarginBlocks: Double = 14.0,

  val halfWidthBlocks: Double = 72.0,
  val centerYBlocks: GenerateHeightSampler = GenerateHeightSampler.relative(0.50),

  val warpBlocksXZ: Double = 26.0,
  val wobbleYScale: Double = 0.030,            // vertical waviness of tears
  override val surfaceFadeStart: Int = 8,
  override val surfaceFadeRamp: Int = 14
) : CaveType, Noised {

  object Noise : NoiseModule {
    object Warp3D : NoiseKey { override val id = "cave.eldritch_vertical_tears.warp3D" }
    object TearAxis2D : NoiseKey { override val id = "cave.eldritch_vertical_tears.axis2D" }
    object TearMod2D : NoiseKey { override val id = "cave.eldritch_vertical_tears.mod2D" }
    object TearWobble3D : NoiseKey { override val id = "cave.eldritch_vertical_tears.wobble3D" }

    override fun install(bank: NoiseBank) {
      bank.register(Warp3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.010)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
      bank.register(TearAxis2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0060) // spacing of tears
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(1)
        }
      }
      bank.register(TearMod2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0030)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
      bank.register(TearWobble3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.016)
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

    val dyCenter = abs(cave.y.toDouble() - centerYBlocks.sampleY(ctx))
    val bandT = ((halfWidthBlocks - dyCenter) / halfWidthBlocks).coerceIn(0.0, 1.0)
    val verticalBandMask = smoothstep01(bandT)
    if (verticalBandMask <= 0.001) return 0.0

    val warp = ctx.noise.get(Noise.Warp3D)
    val wx = cave.worldX + warp.noise3D(cave.worldX, cave.y, cave.worldZ) * warpBlocksXZ
    val wz = cave.worldZ + warp.noise3D(cave.worldX + 999, cave.y, cave.worldZ - 999) * warpBlocksXZ

    // 2D axis field -> carve near zero crossings = slit lines
    val axisBase = ctx.noise.get(Noise.TearAxis2D).noise2D(wx, wz)

    // add vertical wobble so tears aren't perfectly straight walls
    val wobble = ctx.noise.get(Noise.TearWobble3D).noise3D(wx, cave.y * wobbleYScale, wz) * 0.20

    val axis = axisBase + wobble
    val axisDist = abs(axis)

    val slitT = ((slitRadius - axisDist) / slitRadius).coerceIn(0.0, 1.0)
    val slitMask = smoothstep01(slitT).pow(3.5)
    if (slitMask <= 0.001) return 0.0

    // Patchiness to avoid uniform curtain slits everywhere
    val modN = ctx.noise.get(Noise.TearMod2D).noise2D(wx + 333.0, wz - 333.0)
    val mod01 = ((modN + 1.0) * 0.5).coerceIn(0.0, 1.0)
    val presence = smoothstep01(((mod01 - 0.35) / (0.88 - 0.35)).coerceIn(0.0, 1.0))
    if (presence <= 0.001) return 0.0

    val mask = slitMask * presence * verticalBandMask
    return mask * (solidDensity * slitStrength + openMarginBlocks)
  }
}
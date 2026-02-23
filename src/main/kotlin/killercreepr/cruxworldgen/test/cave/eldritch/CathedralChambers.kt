package killercreepr.cruxworldgen.test.cave.eldritch

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.NoiseBank
import killercreepr.cruxworldgen.api.noise.NoiseField
import killercreepr.cruxworldgen.api.noise.NoiseKey
import killercreepr.cruxworldgen.api.noise.NoiseModule
import killercreepr.cruxworldgen.api.noise.Noised
import killercreepr.cruxworldgen.core.feature.GenerateHeightSampler
import kotlin.math.pow

class CathedralChambers(
  val threshold01: Double = 0.80,              // higher = rarer chambers
  val strength: Double = 1.18,
  val openMarginBlocks: Double = 20.0,

  val centerYBlocks: GenerateHeightSampler = GenerateHeightSampler.relative(0.42),
  val halfWidthBlocks: Double = 70.0,

  val horizontalScale: Double = 1.0,
  val verticalScale: Double = 0.55,            // <1 makes chambers taller relative to XZ sampling
  val warpBlocks: Double = 26.0,

  override val surfaceFadeStart: Int = 10,
  override val surfaceFadeRamp: Int = 16
) : CaveType, Noised {

  object Noise : NoiseModule {
    object Warp3D : NoiseKey { override val id = "cave.eldritch_cathedral.warp3D" }
    object Chamber3D : NoiseKey { override val id = "cave.eldritch_cathedral.chamber3D" }
    object Presence2D : NoiseKey { override val id = "cave.eldritch_cathedral.presence2D" }
    object HeightBias2D : NoiseKey { override val id = "cave.eldritch_cathedral.heightBias2D" }

    override fun install(bank: NoiseBank) {
      bank.register(Warp3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.010)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
      bank.register(Chamber3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0062) // big chambers
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
      bank.register(Presence2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0024)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
      bank.register(HeightBias2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0022)
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

    val hBias = ctx.noise.get(Noise.HeightBias2D).noise2D(cave.worldX, cave.worldZ) // [-1..1]
    val centerY = centerYBlocks.sampleY(ctx) + hBias * 18.0

    val dyCenter = kotlin.math.abs(cave.y.toDouble() - centerY)
    val bandT = ((halfWidthBlocks - dyCenter) / halfWidthBlocks).coerceIn(0.0, 1.0)
    val verticalBandMask = smoothstep01(bandT)
    if (verticalBandMask <= 0.001) return 0.0

    val presenceN = ctx.noise.get(Noise.Presence2D).noise2D(cave.worldX, cave.worldZ)
    val presence01 = ((presenceN + 1.0) * 0.5).coerceIn(0.0, 1.0)
    val presence = smoothstep01(((presence01 - 0.50) / (0.92 - 0.50)).coerceIn(0.0, 1.0))
    if (presence <= 0.001) return 0.0

    val warp = ctx.noise.get(Noise.Warp3D)
    val wx = cave.worldX + warp.noise3D(cave.worldX, cave.y, cave.worldZ) * warpBlocks
    val wy = cave.y + warp.noise3D(cave.worldX + 100, cave.y - 200, cave.worldZ + 300) * (warpBlocks * 0.6)
    val wz = cave.worldZ + warp.noise3D(cave.worldX - 400, cave.y + 500, cave.worldZ - 600) * warpBlocks

    val chamberN = ctx.noise.get(Noise.Chamber3D).noise3D(
      wx * horizontalScale,
      wy * verticalScale,
      wz * horizontalScale
    ) // [-1..1]

    val chamber01 = ((chamberN + 1.0) * 0.5).coerceIn(0.0, 1.0)
    val t = ((chamber01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    val chamberMask = smoothstep01(t).pow(2.6)
    if (chamberMask <= 0.001) return 0.0

    val mask = chamberMask * presence * verticalBandMask
    return mask * (solidDensity * strength + openMarginBlocks)
  }

  private fun smoothstep01(t: Double): Double {
    val c = t.coerceIn(0.0, 1.0)
    return c * c * (3.0 - 2.0 * c)
  }
}
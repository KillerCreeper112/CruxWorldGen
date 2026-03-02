package killercreepr.cruxworldgen.standard.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

class OffsetTunnels(
  val noodleRadius: Double = 0.42,
  val verticalRadiusBlocks: Double = 8.0,

  val baseDepthBelowSurface: Double = 46.0,
  val depthVariationBlocks: Double = 18.0,

  val strength: Double = 1.14,
  val openMarginBlocks: Double = 9.0,

  val warpBlocks: Double = 30.0,
  val phaseShiftBlocks: Double = 24.0,         // creates "offset" feeling between nearby tunnel fields
  val halfWidthBlocks: Double = 64.0,
  override val surfaceFadeStart: Int = 6,
  override val surfaceFadeRamp: Int = 12
) : CaveType, Noised {

  object Noise : NoiseModule {
    object Warp3D : NoiseKey { override val id = "cave.eldritch_offset_tunnels.warp3D" }
    object WormA3D : NoiseKey { override val id = "cave.eldritch_offset_tunnels.wormA3D" }
    object WormB3D : NoiseKey { override val id = "cave.eldritch_offset_tunnels.wormB3D" }
    object Height2D : NoiseKey { override val id = "cave.eldritch_offset_tunnels.height2D" }
    object Presence2D : NoiseKey { override val id = "cave.eldritch_offset_tunnels.presence2D" }

    override fun install(bank: NoiseBank) {
      bank.register(Warp3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.015)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
      bank.register(WormA3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0085)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(1)
        }
      }
      bank.register(WormB3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0085)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(1)
        }
      }
      bank.register(Height2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0028)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
      bank.register(Presence2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0036)
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

    // Depth centerline varies in XZ
    val hNoise = ctx.noise.get(Noise.Height2D).noise2D(cave.worldX, cave.worldZ) // [-1..1]
    val centerY = cave.surfaceY - (baseDepthBelowSurface + hNoise * depthVariationBlocks)

    val dy = abs(cave.y.toDouble() - centerY)
    val verticalT = ((verticalRadiusBlocks - dy) / verticalRadiusBlocks).coerceIn(0.0, 1.0)
    val tunnelHeightMask = smoothstep01(verticalT)
    if (tunnelHeightMask <= 0.001) return 0.0

    val bandT = ((halfWidthBlocks - abs(cave.y.toDouble() - centerY)) / halfWidthBlocks).coerceIn(0.0, 1.0)
    val verticalBandMask = smoothstep01(bandT)
    if (verticalBandMask <= 0.001) return 0.0

    val warp = ctx.noise.get(Noise.Warp3D)
    val w = warp.noise3D(cave.worldX, cave.y, cave.worldZ)

    val wx = cave.worldX + w * warpBlocks
    val wy = cave.y + warp.noise3D(cave.worldX + 321, cave.y - 654, cave.worldZ + 987) * (warpBlocks * 0.45)
    val wz = cave.worldZ + warp.noise3D(cave.worldX - 111, cave.y + 222, cave.worldZ - 333) * warpBlocks

    val wormA = ctx.noise.get(Noise.WormA3D).noise3D(wx, wy, wz)
    val wormB = ctx.noise.get(Noise.WormB3D).noise3D(wx + phaseShiftBlocks, wy - phaseShiftBlocks * 0.25, wz - phaseShiftBlocks)

    // Mix two tunnel fields to create broken / offset continuity
    val combined = 0.6 * wormA + 0.4 * wormB
    val axisDist = abs(combined)

    val t = ((noodleRadius - axisDist) / noodleRadius).coerceIn(0.0, 1.0)
    val noodleMask = smoothstep01(t).pow(2.8)
    if (noodleMask <= 0.001) return 0.0

    // Patchy presence to prevent overfilling
    val p = ctx.noise.get(Noise.Presence2D).noise2D(cave.worldX, cave.worldZ)
    val p01 = ((p + 1.0) * 0.5).coerceIn(0.0, 1.0)
    val presence = smoothstep01(((p01 - 0.32) / (0.84 - 0.32)).coerceIn(0.0, 1.0))
    if (presence <= 0.001) return 0.0

    val mask = noodleMask * tunnelHeightMask * verticalBandMask * presence
    return mask * (solidDensity * strength + openMarginBlocks)
  }
}
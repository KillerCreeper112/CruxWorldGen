package killercreepr.cruxworldgen.test.cave.eldritch

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.core.feature.GenerateHeightSampler
import kotlin.math.pow

class VoidPockets(
  val pocketThreshold01: Double = 0.72,         // higher = rarer pockets
  val pocketStrength: Double = 1.10,
  val openMarginBlocks: Double = 8.0,

  val halfWidthBlocks: Double = 54.0,           // vertical band around centerY
  val centerYBlocks: GenerateHeightSampler = GenerateHeightSampler.relative(0.45),

  val warpBlocks: Double = 20.0,
  val pocketScaleBoost: Double = 1.0,           // 0.8..1.4 can make pockets feel larger/smaller
  override val surfaceFadeStart: Int = 6,
  override val surfaceFadeRamp: Int = 10
) : CaveType, Noised {

  object Noise : NoiseModule {
    object Warp3D : NoiseKey { override val id = "cave.eldritch_void_pockets.warp3D" }
    object Pocket3D : NoiseKey { override val id = "cave.eldritch_void_pockets.pocket3D" }
    object Cluster2D : NoiseKey { override val id = "cave.eldritch_void_pockets.cluster2D" }

    override fun install(bank: NoiseBank) {
      bank.register(Warp3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.014)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
      bank.register(Pocket3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.020) // pocket size scale
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
      bank.register(Cluster2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0035) // broad clustering zones
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

    val dy = kotlin.math.abs(cave.y.toDouble() - centerYBlocks.sampleY(ctx))
    val bandT = ((halfWidthBlocks - dy) / halfWidthBlocks).coerceIn(0.0, 1.0)
    val verticalMask = smoothstep01(bandT)
    if (verticalMask <= 0.001) return 0.0

    val clusterN = ctx.noise.get(Noise.Cluster2D).noise2D(cave.worldX, cave.worldZ) // [-1..1]
    val cluster01 = ((clusterN + 1.0) * 0.5).coerceIn(0.0, 1.0)

    // Cluster gate means some areas have many pockets, others almost none
    val clusterMask = smoothstep01(((cluster01 - 0.42) / (0.86 - 0.42)).coerceIn(0.0, 1.0))
    if (clusterMask <= 0.001) return 0.0

    val warp = ctx.noise.get(Noise.Warp3D)
    val wx = cave.worldX + warp.noise3D(cave.worldX, cave.y, cave.worldZ) * warpBlocks
    val wy = cave.y + warp.noise3D(cave.worldX + 777, cave.y - 333, cave.worldZ + 111) * (warpBlocks * 0.55)
    val wz = cave.worldZ + warp.noise3D(cave.worldX - 444, cave.y, cave.worldZ + 999) * warpBlocks

    val p = ctx.noise.get(Noise.Pocket3D).noise3D(wx * pocketScaleBoost, wy * pocketScaleBoost, wz * pocketScaleBoost)
    val p01 = ((p + 1.0) * 0.5).coerceIn(0.0, 1.0)

    val t = ((p01 - pocketThreshold01) / (1.0 - pocketThreshold01)).coerceIn(0.0, 1.0)
    val pocketMask = smoothstep01(t).pow(2.8)

    if (pocketMask <= 0.001) return 0.0

    val mask = pocketMask * verticalMask * (0.35 + 0.65 * clusterMask)
    return mask * (solidDensity * pocketStrength + openMarginBlocks)
  }

  private fun smoothstep01(t: Double): Double {
    val c = t.coerceIn(0.0, 1.0)
    return c * c * (3.0 - 2.0 * c)
  }
}
package killercreepr.cruxworldgen.standard.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.util.Curve
import kotlin.math.abs
import kotlin.math.max

class HorizontalNoodleCaves(
  val noodleRadiusA: Double = 0.20,
  val noodleRadiusB: Double = 0.20,
  val strength: Double = 1.12,
  val openMarginBlocks: Double = 6.0,
  val warpBlocks: Double = 16.0,

  val wormYScale: Double = 0.05,   // very low => horizontal bias
  val warpYScale: Double = 0.03,   // very low => stable warp across heights

  val deepStart: Double = 4.0,
  val deepFull: Double = 16.0,
  val nearSurfaceDepth: Double = 10.0,
  val breakThreshold: Double = 0.80,

  // optional soft preferred depth, but not a hard layer
  val preferredDepth: Double = 30.0,
  val preferredHalfWidth: Double = 22.0,
  val depthPreferenceMin: Double = 0.35,

  override val surfaceFadeStart: Int = 4,
  override val surfaceFadeRamp: Int = 12
) : CaveType, Noised {

  object Noise : NoiseModule {
    object Warp3D : NoiseKey { override val id = "cave.horizontal_noodle.warp3D" }
    object WormA3D : NoiseKey { override val id = "cave.horizontal_noodle.wormA3D" }
    object WormB3D : NoiseKey { override val id = "cave.horizontal_noodle.wormB3D" }
    object Entrance2D : NoiseKey { override val id = "cave.horizontal_noodle.entrance2D" }
    object Depth2D : NoiseKey { override val id = "cave.horizontal_noodle.depth2D" }

    override fun install(bank: NoiseBank) {
      bank.register(Warp3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.010)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
      bank.register(WormA3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.006)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
      bank.register(WormB3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0075)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
      bank.register(Entrance2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0015)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
      bank.register(Depth2D) { seed ->
        NoiseField.noiseField(seed) {
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
    if (cave.depthBelowSurface < 0) return 0.0

    val warpY = (cave.y * warpYScale).toInt()
    val warp = ctx.noise.get(Noise.Warp3D).noise3D(cave.worldX, warpY, cave.worldZ)

    val wx = (cave.worldX + warp * warpBlocks).toInt()
    val wz = (cave.worldZ + warp * warpBlocks).toInt()
    val wy = (cave.y * wormYScale).toInt()

    val wormA = ctx.noise.get(Noise.WormA3D).noise3D(wx, wy, wz)
    val wormB = ctx.noise.get(Noise.WormB3D).noise3D(wx + 137, wy, wz - 211)

    val aT = ((noodleRadiusA - abs(wormA)) / noodleRadiusA).coerceIn(0.0, 1.0)
    val bT = ((noodleRadiusB - abs(wormB)) / noodleRadiusB).coerceIn(0.0, 1.0)

    val aMask = Curve.smoothstep01(aT)
    val bMask = Curve.smoothstep01(bT)

    val noodleMask = aMask * bMask
    if (noodleMask <= 0.001) return 0.0

    val verticalMask = CaveMasks.depthWithBreakthrough(
      ctx = ctx,
      cave = cave,
      noiseKey = Noise.Entrance2D,
      deepStart = deepStart,
      deepFull = deepFull,
      nearSurfaceDepth = nearSurfaceDepth,
      breakThreshold = breakThreshold
    )

    // Soft preferred depth so they tend to run in horizontal belts,
    // but can still exist all the way down.
    val depthNoise = ctx.noise.get(Noise.Depth2D).noise2D(cave.worldX, cave.worldZ)
    val localPreferredDepth = preferredDepth + depthNoise * 10.0
    val centerY = cave.surfaceY - localPreferredDepth

    val dy = abs(cave.y.toDouble() - centerY)
    val prefT = ((preferredHalfWidth - dy) / preferredHalfWidth).coerceIn(0.0, 1.0)
    //val depthPreference = depthPreferenceMin + Curve.smoothstep01(prefT) * (1.0 - depthPreferenceMin)

    val surfaceMask = CaveMasks.depthWithBreakthrough(
      ctx = ctx,
      cave = cave,
      noiseKey = Noise.Entrance2D,
      deepStart = deepStart,
      deepFull = deepFull,
      nearSurfaceDepth = nearSurfaceDepth,
      breakThreshold = breakThreshold
    )

    val depthPreference = CaveMasks.softDepthPreference(
      cave = cave,
      centerDepth = 30.0,
      halfWidth = 20.0,
      minValue = 0.25
    )
    val mask = noodleMask * surfaceMask * depthPreference
    if (mask <= 0.001) return 0.0

    return mask * (solidDensity * strength + openMarginBlocks)
  }
}
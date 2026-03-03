package killercreepr.cruxworldgen.standard.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class NoodleCaves(
  // Thickness / shape
  val verticalRadiusBlocks: Double = 7.8,

  val ridgeWidth01: Double = 0.5,

  // where tunnels tend to live below surface
  val baseDepthBelowSurface: Double = 28.0,
  val depthVariationBlocks: Double = 16.0,

  // how much the paths meander in XZ
  val warpBlocks: Double = 12.0,

  // carve strength
  val strength: Double = 1.10,
  val openMarginBlocks: Double = 8.0,

  // vertical placement / surface behavior
  val deepStart: Double = 6.0,
  val deepFull: Double = 18.0,
  val nearSurfaceDepth: Double = 10.0,
  val breakThreshold: Double = 0.82,

  override val surfaceFadeStart: Int = 3,
  override val surfaceFadeRamp: Int = 10
) : CaveType, Noised {

  object Noise : NoiseModule {
    object PathA2D : NoiseKey { override val id = "cave.noodle.path_a_2D" }
    object PathB2D : NoiseKey { override val id = "cave.noodle.path_b_2D" }

    object CenterY2D : NoiseKey { override val id = "cave.noodle.center_y_2D" }

    object WarpX2D : NoiseKey { override val id = "cave.noodle.warp_x_2D" }
    object WarpZ2D : NoiseKey { override val id = "cave.noodle.warp_z_2D" }

    object SurfaceBreak2D : NoiseKey { override val id = "cave.noodle.surface_break_2D" }

    override fun install(bank: NoiseBank) {
      bank.register(PathA2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.015)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }

      bank.register(PathB2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.011)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }

      bank.register(CenterY2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.007)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }

      bank.register(WarpX2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.010)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }

      bank.register(WarpZ2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.010)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }

      bank.register(SurfaceBreak2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.02)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
    }
  }

  override val noiseModule = Noise

  override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    val solidDensity = max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0
    if (cave.depthBelowSurface < 0) return 0.0

    val warpX = ctx.noise.get(Noise.WarpX2D).noise2D(cave.worldX, cave.worldZ) * warpBlocks
    val warpZ = ctx.noise.get(Noise.WarpZ2D).noise2D(cave.worldX, cave.worldZ) * warpBlocks

    val wx = cave.worldX + warpX
    val wz = cave.worldZ + warpZ

    // Near-zero contours of 2D noise create long spaghetti/noodle-like paths.
    val a = abs(ctx.noise.get(Noise.PathA2D).noise2D(wx, wz))
    val b = abs(ctx.noise.get(Noise.PathB2D).noise2D(wx, wz))

    // Use whichever field is closer to zero so we get more connected winding lines.
    val pathDistance01 = min(a, b)

    // 1 at noodle centerline in XZ, falls off outward.
    val horizontalMask = 1.0 - smoothstep01((pathDistance01 / ridgeWidth01).coerceIn(0.0, 1.0))
    if (horizontalMask <= 0.001) return 0.0

    // Each XZ location gets a local preferred depth below surface, so tunnels rise/fall gradually.
    val centerOffset = ctx.noise.get(Noise.CenterY2D).noise2D(wx, wz) * depthVariationBlocks
    val targetDepth = baseDepthBelowSurface + centerOffset

    val depthDelta = abs(cave.depthBelowSurface - targetDepth)

    // Vertical cross-section of the tunnel.
    val verticalMask = 1.0 - smoothstep01((depthDelta / verticalRadiusBlocks).coerceIn(0.0, 1.0))
    if (verticalMask <= 0.001) return 0.0

    // Slightly rounder / tighter tube feeling
    val tubeMask = horizontalMask * horizontalMask * horizontalMask * verticalMask

    val placementMask = CaveMasks.depthWithBreakthrough(
      ctx = ctx,
      cave = cave,
      noiseKey = Noise.SurfaceBreak2D,
      deepStart = deepStart,
      deepFull = deepFull,
      nearSurfaceDepth = nearSurfaceDepth,
      breakThreshold = breakThreshold
    )

    val mask = tubeMask * placementMask
    if (mask <= 0.001) return 0.0

    return mask * (solidDensity * strength + openMarginBlocks)
  }
}
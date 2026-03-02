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
import kotlin.math.abs
import kotlin.math.max

class SpaghettiCaves(
  val noodleRadius: Double = 1.0,        // thickness of noodles in XZ-mask space
  val verticalRadiusBlocks: Double = 6.0, // thickness in Y (tunnel "height")
  val baseDepthBelowSurface: Double = 28.0, // average depth of noodle network
  val depthVariationBlocks: Double = 14.0,  // how much centerline moves up/down
  val strength: Double = 1.15,
  val openMarginBlocks: Double = 6.0,
  val warpBlocks: Double = 22.0,
  override val surfaceFadeStart : Int = 0,
  override val surfaceFadeRamp: Int = 0
) : CaveType, Noised {

  object Noise : NoiseModule {
    object Warp3D : NoiseKey { override val id = "cave.spaghetti.warp3D" }
    object Worm3D : NoiseKey { override val id = "cave.spaghetti.worm3D" }
    object Height2D : NoiseKey { override val id = "cave.spaghetti.height2D" }
    object Entrance2D : NoiseKey { override val id = "cave.spaghetti.entrance2D" }

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
      bank.register(Entrance2D){ seed ->
        NoiseField.Companion.noiseField(seed){
          frequency(0.0015) // big blobs
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

    // ---------- Pick a center Y for the spaghetti layer ----------
    /*val heightNoise = ctx.noise.get(Noise.Height2D).noise2D(cave.worldX, cave.worldZ) // [-1..1]
    val targetDepth = baseDepthBelowSurface + heightNoise * depthVariationBlocks
    val centerY = cave.surfaceY - targetDepth

    // Only carve near that centerline (prevents vertical shafts)
    val dy = kotlin.math.abs(cave.y.toDouble() - centerY)
    val vT = ((verticalRadiusBlocks - dy) / verticalRadiusBlocks).coerceIn(0.0, 1.0)
    val verticalMask = vT * vT * (3.0 - 2.0 * vT)
    if (verticalMask <= 0.001) return 0.0*/

    val entranceN = ctx.noise.get(Noise.Entrance2D).noise2D(cave.worldX, cave.worldZ) // [-1..1]
    val entrance01 = ((entranceN * 0.5) + 0.5) // [0..1]

// Make entrances rare by thresholding
    val e = ((entrance01 - 0.78) / (1.0 - 0.78)).coerceIn(0.0, 1.0) // 0 unless in top ~22%
    val entranceMask = Curve.smoothstep01(e)

    val heightNoise = ctx.noise.get(Noise.Height2D).noise2D(cave.worldX, cave.worldZ) // [-1..1]
    val baseTargetDepth = baseDepthBelowSurface + heightNoise * depthVariationBlocks

// Lift towards surface in entrance zones (minDepth controls how open it can get)
    val minDepth = 2.0
    val targetDepth = baseTargetDepth * (1.0 - entranceMask) + minDepth * entranceMask

    val centerY = cave.surfaceY - targetDepth

// Optional: widen vertical radius inside entrances so it actually reaches the surface
    val vRadius = verticalRadiusBlocks + entranceMask * 8.0
    val dy = abs(cave.y.toDouble() - centerY)
    val vT = ((vRadius - dy) / vRadius).coerceIn(0.0, 1.0)
    val verticalMask = vT * vT * (3.0 - 2.0 * vT)
    if (verticalMask <= 0.001) return 0.0


    // ---------- Build a noodle network in XZ ----------
    // Warp in XZ only (stable with Y)
    val warp = ctx.noise.get(Noise.Warp3D).noise3D(cave.worldX, 0, cave.worldZ) // using y=0 intentionally
    val wx = (cave.worldX + warp * warpBlocks).toInt()
    val wz = (cave.worldZ + warp * warpBlocks).toInt()

    // Worm noise sampled with constant y -> gives an XZ noodle network
    val worm = ctx.noise.get(Noise.Worm3D).noise3D(wx, 0, wz) // using y=0 intentionally
    val axisDist = abs(worm)

    val nT = ((noodleRadius - axisDist) / noodleRadius).coerceIn(0.0, 1.0)
    val noodleMask = nT * nT * nT

    // Cutoff to prevent tiny 1–2 block pimples
    //if (noodleMask < 0.55) return 0.0
    if (noodleMask < 0.35) return 0.0

    val mask = noodleMask * verticalMask

    // Scalable carve: relative to local terrain density
    return mask * (solidDensity * strength + openMarginBlocks)
  }
}
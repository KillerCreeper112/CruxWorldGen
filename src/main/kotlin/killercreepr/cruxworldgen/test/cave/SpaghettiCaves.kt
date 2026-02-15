package killercreepr.cruxworldgen.test.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*

class SpaghettiCaves(
  val noodleRadius: Double = 1.0,        // thickness of noodles in XZ-mask space
  val verticalRadiusBlocks: Double = 6.0, // thickness in Y (tunnel "height")
  val baseDepthBelowSurface: Double = 28.0, // average depth of noodle network
  val depthVariationBlocks: Double = 14.0,  // how much centerline moves up/down
  val strength: Double = 1.15,
  val openMarginBlocks: Double = 6.0,
  val warpBlocks: Double = 22.0,
  override val surfaceFadeStart : Int = 0,
  override val surfaceFadeRamp: Int = 16
) : CaveType, Noised {

  object Noise : NoiseModule{
    object Warp3D : NoiseKey{ override val id = "cave.spaghetti.warp3D" }
    object Worm3D : NoiseKey{ override val id = "cave.spaghetti.worm3D" }
    object Height2D : NoiseKey{ override val id = "cave.spaghetti.height2D" }

    override fun install(bank: NoiseBank) {
      bank.register(Warp3D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.018)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
      bank.register(Worm3D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.008)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
      bank.register(Height2D){ seed ->
        NoiseField.noiseField(seed){
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
    val solidDensity = kotlin.math.max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0
    //if (cave.depthBelowSurface <= 0) return 0.0

    // ---------- Pick a center Y for the spaghetti layer ----------
    val heightNoise = ctx.noise.get(Noise.Height2D).noise2D(cave.worldX, cave.worldZ) // [-1..1]
    val targetDepth = baseDepthBelowSurface + heightNoise * depthVariationBlocks
    val centerY = cave.surfaceY - targetDepth

    // Only carve near that centerline (prevents vertical shafts)
    val dy = kotlin.math.abs(cave.y.toDouble() - centerY)
    val vT = ((verticalRadiusBlocks - dy) / verticalRadiusBlocks).coerceIn(0.0, 1.0)
    val verticalMask = vT * vT * (3.0 - 2.0 * vT)
    if (verticalMask <= 0.001) return 0.0

    // ---------- Build a noodle network in XZ ----------
    // Warp in XZ only (stable with Y)
    val warp = ctx.noise.get(Noise.Warp3D).noise3D(cave.worldX, 0, cave.worldZ) // using y=0 intentionally
    val wx = (cave.worldX + warp * warpBlocks).toInt()
    val wz = (cave.worldZ + warp * warpBlocks).toInt()

    // Worm noise sampled with constant y -> gives an XZ noodle network
    val worm = ctx.noise.get(Noise.Worm3D).noise3D(wx, 0, wz) // using y=0 intentionally
    val axisDist = kotlin.math.abs(worm)

    val nT = ((noodleRadius - axisDist) / noodleRadius).coerceIn(0.0, 1.0)
    val noodleMask = nT * nT * nT

    // Cutoff to prevent tiny 1–2 block pimples
    if (noodleMask < 0.55) return 0.0

    val mask = noodleMask * verticalMask

    // Scalable carve: relative to local terrain density
    return mask * (solidDensity * strength + openMarginBlocks)
  }
}
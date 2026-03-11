package killercreepr.cruxworldgen.test.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*

class WideThinVerticalCaves(
  val radius: Double = 0.06,        // smaller = rarer/thinner
  val strength: Double = 1.10,      // MUST be > 1 to open when mask ~ 1
  val openMarginBlocks: Double = 6.0, // thickness / reliability,
  override val surfaceFadeStart : Int = 0,
  override val surfaceFadeRamp: Int = 16
) : CaveType, Noised {

  object Noise : NoiseModule{
    object Warp3D : NoiseKey{ override val id = "cave.wide_thin_vertical.warp3D" }
    object Worm3D : NoiseKey{ override val id = "cave.wide_thin_vertical.worm3D" }
    object Height2D : NoiseKey{ override val id = "cave.wide_thin_vertical.height2D" }

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
    //val solidDensity = kotlin.math.max(0.0, cave.terrainDensity)
    //if (solidDensity <= 0.0) return 0.0

    //val wormNoise = ctx.noise.caveWorm3D(cave.worldX, cave.y, cave.worldZ) // signed
    //val axisDistance = kotlin.math.abs(wormNoise)

    // mask in [0..1]
    //val normalized = ((radius - axisDistance) / radius).coerceIn(0.0, 1.0)
    //val mask = normalized * normalized * normalized

    // The scalable carve: beats solidDensity near centerline without "+ depth" hacks
    //return mask * (solidDensity * strength + openMarginBlocks)

    val solidDensity = maxOf(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0

    val warp = ctx.noise.get(Noise.Warp3D).noise3D(cave.worldX, 0, cave.worldZ)
    val wx = (cave.worldX + warp * 20.0).toInt()
    val wz = (cave.worldZ + warp * 20.0).toInt()

    val worm = ctx.noise.get(Noise.Worm3D).noise3D(wx.toDouble(), cave.y * 0.25, wz.toDouble())
    val axisDist = kotlin.math.abs(worm)

    val radius = 0.07
    val t = ((radius - axisDist) / radius).coerceIn(0.0, 1.0)
    val mask = t * t * (3.0 - 2.0 * t)

    if (mask < 0.55) return 0.0

    return mask * (solidDensity * 1.10 + 6.0)

  }
}
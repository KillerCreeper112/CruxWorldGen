package killercreepr.cruxworldgen.standard.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.util.Curve
import killercreepr.cruxworldgen.extension.remap01
import kotlin.math.max

class Standard3DCaves(
  val warpXZ: Double = 25.0,
  val warpY: Double = 15.0,

  val threshold: Double = 0.68,
  val ramp: Double = 0.15,

  override val surfaceFadeStart: Int = 3,
  override val surfaceFadeRamp: Int = 16
) : CaveType, Noised {

  object Noise : NoiseModule {
    object Carve3D : NoiseKey { override val id = "cave.standard3D.carve3D" }
    object WarpX3D : NoiseKey { override val id = "cave.standard3D.warp_x3D" }
    object WarpY3D : NoiseKey { override val id = "cave.standard3D.warp_y3D" }
    object WarpZ3D : NoiseKey { override val id = "cave.standard3D.warp_z3D" }


    override fun install(bank: NoiseBank) {
      bank.register(Carve3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.012)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }

      bank.register(WarpX3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.025)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }

      bank.register(WarpY3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.007)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }

      bank.register(WarpZ3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.019)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(3)
        }
      }
    }
  }

  override val noiseModule = Noise

  override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    val solidDensity = max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0

    val x = cave.worldX
    val y = cave.y
    val z = cave.worldZ

    val wx = x + ctx.noise.get(Noise.WarpX3D).noise3D(x, y, z) * warpXZ
    val wy = y + ctx.noise.get(Noise.WarpY3D).noise3D(x, y, z) * warpY
    val wz = z + ctx.noise.get(Noise.WarpZ3D).noise3D(x, y, z) * warpXZ
    val carveN = ctx.noise.get(Noise.Carve3D).noise3D(wx, wy, wz).remap01()


    val carve = Curve.smoothstep(threshold, threshold + ramp, carveN)

    return solidDensity * carve
  }
}
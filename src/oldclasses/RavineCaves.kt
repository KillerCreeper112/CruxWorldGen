package killercreepr.cruxworldgen.standard.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.util.Curve
import killercreepr.cruxworldgen.extension.remap01
import kotlin.math.max

class RavineCaves(
  val warpXZ: Double = 15.0,
  val warpY: Double = 8.0,

  val threshold: Double = 0.68,
  val ramp: Double = 0.08,

  override val surfaceFadeStart: Int = 3,
  override val surfaceFadeRamp: Int = 16,

  val noise : Noise = StandardNoise
) : CaveType, Noised {

  interface Noise : NoiseModule {
    val Carve3D : NoiseKey
    val WarpX3D : NoiseKey
    val WarpY3D : NoiseKey
    val WarpZ3D : NoiseKey
    val Vertical2D : NoiseKey
  }

  object StandardNoise : Noise {
    override val Carve3D = object: NoiseKey { override val id = "cave.ravine.carve3D" }
    override val  WarpX3D = object: NoiseKey { override val id = "cave.ravine.warp_x3D" }
    override val  WarpY3D = object: NoiseKey { override val id = "cave.ravine.warp_y3D" }
    override val  WarpZ3D = object: NoiseKey { override val id = "cave.ravine.warp_z3D" }
    override val  Vertical2D = object: NoiseKey { override val id = "cave.ravine.vertical2D" }

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

      bank.register(Vertical2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.009)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.Ridged)
            .fractalOctaves(2)
        }
      }
    }
  }

  override val noiseModule = noise

  override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    val solidDensity = max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0

    val x = cave.worldX.toDouble()
    val y = cave.y.toDouble()
    val z = cave.worldZ.toDouble()

    val warpXField = ctx.noise.get(noise.WarpX3D)
    val warpYField = ctx.noise.get(noise.WarpY3D)
    val warpZField = ctx.noise.get(noise.WarpZ3D)
    val carveField = ctx.noise.get(noise.Carve3D)
    val verticalField = ctx.noise.get(noise.Vertical2D)

    val wx = x + warpXField.noise3D(x, y, z) * warpXZ
    val wy = y + warpYField.noise3D(x, y, z) * warpY
    val wz = z + warpZField.noise3D(x, y, z) * warpXZ

    val angle = 35.0 * Math.PI / 180.0
    val c = kotlin.math.cos(angle)
    val s = kotlin.math.sin(angle)

    val rx = wx * c + wz * s
    val rz = -wx * s + wz * c

    val carveN = carveField.noise3D(
      rx * 0.04,
      wy * 1.5,
      rz * 0.4
    ).remap01()

    val carve = Curve.smoothstep(0.72, 0.80, carveN)

    val pathBias = verticalField.noise2D(wx * 0.35, wz * 0.35).remap01()
    val pathMask = Curve.smoothstep(0.45, 0.75, pathBias)

    val finalCarve = carve * (0.65 + pathMask * 0.35)

    return solidDensity * finalCarve
  }
}
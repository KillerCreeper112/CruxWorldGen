package killercreepr.cruxworldgen.standard.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.util.Curve
import killercreepr.cruxworldgen.extension.remap01
import kotlin.math.max

class StandardLarge3DCaves(
  val warpXZ: Double = 25.0,
  val warpY: Double = 15.0,

  val threshold: Double = 0.68,
  val ramp: Double = 0.15,
  val platformStrength: Double = 1.5,

  override val surfaceFadeStart: Int = 3,
  override val surfaceFadeRamp: Int = 16,
  override val surfaceOpenChance: Double = 1.0,

  val noise : Noise = StandardNoise
) : CaveType.HasSurfaceOpenings, Noised {

  interface Noise : NoiseModule {
    val Carve3D : NoiseKey
    val Platform3D : NoiseKey
    val WarpX3D : NoiseKey
    val WarpY3D : NoiseKey
    val WarpZ3D : NoiseKey
  }

  object StandardNoise : Noise {
    override val Carve3D = object: NoiseKey { override val id = "cave.standard_large3D.carve3D" }
    override val Platform3D = object: NoiseKey { override val id = "cave.standard_large3D.platform3D" }
    override val WarpX3D = object: NoiseKey { override val id = "cave.standard_large3D.warp_x3D" }
    override val WarpY3D = object: NoiseKey { override val id = "cave.standard_large3D.warp_y3D" }
    override val WarpZ3D = object: NoiseKey { override val id = "cave.standard_large3D.warp_z3D" }

    override fun install(bank: NoiseBank) {
      bank.register(Carve3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.012)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
      bank.register(Platform3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.009)
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

  override val noiseModule = noise

  override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    val solidDensity = max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0

    val x = cave.worldX
    val y = cave.y
    val z = cave.worldZ

    val wx = x + ctx.noise.get(noise.WarpX3D).noise3D(x, y, z) * warpXZ
    val wy = y + ctx.noise.get(noise.WarpY3D).noise3D(x, y, z) * warpY
    val wz = z + ctx.noise.get(noise.WarpZ3D).noise3D(x, y, z) * warpXZ
    val carveN = ctx.noise.get(noise.Carve3D).noise3D(wx * 0.5, wy, wz * 0.3).remap01()

    val carve = Curve.smoothstep(threshold, threshold + ramp, carveN)

    return solidDensity * carve// + platformN
  }
}
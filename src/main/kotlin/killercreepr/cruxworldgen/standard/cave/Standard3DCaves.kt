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

  val threshold: Double = 0.6,
  val ramp: Double = 0.2,

  override val surfaceFadeStart: Int = 3,
  override val surfaceFadeRamp: Int = 16,
  override val surfaceOpenChance: Double = 0.5,

  val noise : Noise = StandardNoise
) : CaveType.HasSurfaceOpenings, Noised {

  interface Noise : NoiseModule {
    val Carve3D : NoiseKey
    val WarpX3D : NoiseKey
    val WarpY3D : NoiseKey
    val WarpZ3D : NoiseKey
  }

  object StandardNoise : Noise {
    override val Carve3D = object: NoiseKey { override val id = "cave.standard3D.carve3D" }
    override val  WarpX3D = object: NoiseKey { override val id = "cave.standard3D.warp_x3D" }
    override val  WarpY3D = object: NoiseKey { override val id = "cave.standard3D.warp_y3D" }
    override val  WarpZ3D = object: NoiseKey { override val id = "cave.standard3D.warp_z3D" }

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

  override val noiseModule = noise

  override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    val solidDensity = max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0

    val x = cave.worldX
    val y = cave.y
    val z = cave.worldZ

    val warpXNoise = ctx.noise.get(noise.WarpX3D)
    val warpYNoise = ctx.noise.get(noise.WarpY3D)
    val warpZNoise = ctx.noise.get(noise.WarpZ3D)
    val carveNoise = ctx.noise.get(noise.Carve3D)

    val wx = x + warpXNoise.noise3D(x, y, z) * warpXZ
    val wy = y + warpYNoise.noise3D(x, y, z) * warpY
    val wz = z + warpZNoise.noise3D(x, y, z) * warpXZ

    val raw = carveNoise.noise3D(wx, wy, wz) // [-1, 1]

    val startRaw = threshold * 2.0 - 1.0
    val endRaw = (threshold + ramp) * 2.0 - 1.0

    if (raw <= startRaw) return 0.0

    val carveStrength = solidDensity + 0.8
    if (raw >= endRaw) return carveStrength

    val carveN = (raw + 1.0) * 0.5
    val carve = Curve.smoothstep(threshold, threshold + ramp, carveN)

    return carve * carveStrength
  }
}
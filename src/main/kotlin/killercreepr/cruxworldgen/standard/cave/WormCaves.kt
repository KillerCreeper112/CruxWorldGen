package killercreepr.cruxworldgen.standard.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.util.Curve
import killercreepr.cruxworldgen.extension.remap01
import kotlin.math.max
import kotlin.math.sqrt

class WormCaves(
  val strength: Double = 1.5,
  val warpXZ: Double = 25.0,
  val warpY: Double = 15.0,

  val radius: Double = 0.07,
  val feather: Double = 0.07,

  override val surfaceFadeStart: Int = 3,
  override val surfaceFadeRamp: Int = 16,
  override val surfaceOpenChance: Double = 0.65,

  val noise : Noise = StandardNoise
) : CaveType.HasSurfaceOpenings, Noised {

  interface Noise : NoiseModule{
    val PathA3D : NoiseKey
    val PathB3D : NoiseKey
    val WarpX3D : NoiseKey
    val WarpY3D : NoiseKey
    val WarpZ3D : NoiseKey
  }

  object StandardNoise : Noise {
    override val PathA3D = object : NoiseKey { override val id = "cave.spaghetti.path_a3D" }
    override val PathB3D = object : NoiseKey { override val id = "cave.spaghetti.path_b3D" }
    override val WarpX3D = object : NoiseKey { override val id = "cave.spaghetti.warp_x3D" }
    override val WarpY3D = object : NoiseKey { override val id = "cave.spaghetti.warp_y3D" }
    override val WarpZ3D = object : NoiseKey { override val id = "cave.spaghetti.warp_z3D" }


    override fun install(bank: NoiseBank) {
      bank.register(PathA3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.005)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2S)
            .fractalType(CruxNoise.FractalType.None)
            .rotationType3D(CruxNoise.RotationType3D.ImproveXZPlanes)
            .domainWarpType(CruxNoise.DomainWarpType.OpenSimplex2Reduced)
            .fractalOctaves(2)
        }
      }
      bank.register(PathB3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.01)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2S)
            .fractalType(CruxNoise.FractalType.None)
            .rotationType3D(CruxNoise.RotationType3D.ImproveXZPlanes)
            .domainWarpType(CruxNoise.DomainWarpType.OpenSimplex2Reduced)
            .fractalOctaves(1)
        }
      }

      bank.register(WarpX3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.01)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2S)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }

      bank.register(WarpY3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.01)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2S)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }

      bank.register(WarpZ3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.01)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2S)
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

    val rx = wx * 0.866 + wz * 0.5
    val rz = -wx * 0.5 + wz * 0.866

    val a = kotlin.math.abs(ctx.noise.get(noise.PathA3D).noise3D(wx, wy, wz))
    val b = kotlin.math.abs(ctx.noise.get(noise.PathB3D).noise3D(rx, wy, rz))
    val d = sqrt(a * a + b * b)
    val tube = 1.0 - Curve.smoothstep(radius, radius + feather, d)

    val tubeTight = tube * tube * tube

    return solidDensity * tubeTight * strength
  }
}
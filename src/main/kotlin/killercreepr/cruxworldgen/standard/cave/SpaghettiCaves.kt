package killercreepr.cruxworldgen.standard.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cache.CoarseCache
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.util.Curve
import killercreepr.cruxworldgen.api.util.MathUtil.cornerIndex
import killercreepr.cruxworldgen.extension.remap01
import kotlin.math.max

class SpaghettiCaves(
  val strength: Double = 1.7,
  val warpXZ: Double = 25.0,
  val warpY: Double = 15.0,

  val radius: Double = 0.14,
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
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.Ridged)
            .fractalOctaves(2)
        }
      }
      bank.register(PathB3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.01)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.Ridged)
            .fractalOctaves(1)
        }
      }

      bank.register(WarpX3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.012)
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

  private val outer = radius + feather
  private val outer2 = outer * outer
  private val radius2 = radius * radius

  private val rotCos = 0.8660254037844386
  private val rotSin = 0.5
  override fun carveBlocks(
    ctx: GenerateContext,
    cave: CaveContext,
    cache: CoarseCache
  ): Double {
    val solidDensity = max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0
    val wx = cache.interpolate { (it as Cache).warpX }
    val wy = cache.interpolate { (it as Cache).warpY }
    val wz = cache.interpolate { (it as Cache).warpZ }

    val rx = wx * rotCos + wz * rotSin
    val rz = -wx * rotSin + wz * rotCos

    val pathA = ctx.noise.get(noise.PathA3D).noise3D(wx,wy,wz).remap01()

    val da = 1.0 - pathA
    if (da >= outer) return 0.0


    val pathB = ctx.noise.get(noise.PathB3D).noise3D(rx, wy, rz).remap01()
    val db = 1.0 - pathB
    if (db >= outer) return 0.0


    val d2 = da * da + db * db
    if (d2 >= outer2) return 0.0
    if (d2 <= radius2) return solidDensity * strength

    val tube = 1.0 - Curve.smoothstep(radius2, outer2, d2)

    val tubeTight = tube * tube * tube

    return solidDensity * tubeTight * strength
  }

  override fun coarseCache(
    ctx: GenerateContext,
    worldX: Int,
    worldY: Int,
    worldZ: Int,
    terrainDensity: Double
  ): CoarseCache? {
    /*val solidDensity = max(0.0, terrainDensity)
    if (solidDensity <= 0.0) return null*/

    val x = worldX
    val y = worldY
    val z = worldZ

    val wx = x + ctx.noise.get(noise.WarpX3D).noise3D(x, y, z) * warpXZ
    val wy = y + ctx.noise.get(noise.WarpY3D).noise3D(x, y, z) * warpY
    val wz = z + ctx.noise.get(noise.WarpZ3D).noise3D(x, y, z) * warpXZ
    return Cache(
      wx, wy, wz
    )
  }



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

    val outer = radius + feather
    val outer2 = outer * outer
    val radius2 = radius * radius

    val pathA = ctx.noise.get(noise.PathA3D).noise3D(wx,wy,wz).remap01()

    val da = 1.0 - pathA
    if (da >= outer) return 0.0


    val pathB = ctx.noise.get(noise.PathB3D).noise3D(rx, wy, rz).remap01()
    val db = 1.0 - pathB
    if (db >= outer) return 0.0


    val d2 = da * da + db * db
    if (d2 >= outer2) return 0.0
    if (d2 <= radius2) return solidDensity * strength

    val tube = 1.0 - Curve.smoothstep(radius2, outer2, d2)

    val tubeTight = tube * tube * tube

    return solidDensity * tubeTight * strength
  }

  data class Cache(
    val warpX: ScalarField3D,
    val warpY: ScalarField3D,
    val warpZ: ScalarField3D
  ) : CoarseCache
}

interface ScalarField3D {
  fun sample(tx: Double, ty: Double, tz: Double): Double
}

class CornerDoubleField3D(
  val c000: Double,
  val c100: Double,
  val c010: Double,
  val c110: Double,

  val c001: Double,
  val c101: Double,
  val c011: Double,
  val c111: Double,
) : ScalarField3D {
  override fun sample(tx: Double, ty: Double, tz: Double): Double {
    return Curve.trilerp(c000, c100, c010, c110, c001, c101, c011, c111, tx, ty, tz)
  }
}
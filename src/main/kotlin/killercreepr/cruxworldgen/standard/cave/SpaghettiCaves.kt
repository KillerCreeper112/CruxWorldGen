package killercreepr.cruxworldgen.standard.cave

import killercreepr.crux.core.Crux
import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cache.CoarseCache
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.util.Curve
import killercreepr.cruxworldgen.api.util.MathUtil.cornerIndex
import killercreepr.cruxworldgen.extension.remap01
import net.minecraft.world.level.levelgen.NoiseChunk
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
) : CaveType.HasSurfaceOpenings<SpaghettiCaves.CornerCache, SpaghettiCaves.BlockCache>, Noised {

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

  data class CornerCache(val warpX: Double, val warpY: Double, val warpZ: Double)
  data class BlockCache(val warpX: Double, val warpY: Double, val warpZ: Double)

  override fun coarseCache(
    ctx: GenerateContext,
    worldX: Int,
    worldY: Int,
    worldZ: Int,
    terrainDensity: Double
  ): CornerCache? {
    val x = worldX
    val y = worldY
    val z = worldZ

    val warpXNoise = ctx.noise.get(noise.WarpX3D)
    val warpYNoise = ctx.noise.get(noise.WarpY3D)
    val warpZNoise = ctx.noise.get(noise.WarpZ3D)

    val wx = warpXNoise.noise3D(x, y, z) * warpXZ
    val wy = warpYNoise.noise3D(x, y, z) * warpY
    val wz = warpZNoise.noise3D(x, y, z) * warpXZ
    return CornerCache(
      wx, wy, wz
    )
  }

  override fun interpolateCache(
    c000: CornerCache,
    c100: CornerCache,
    c010: CornerCache,
    c110: CornerCache,
    c001: CornerCache,
    c101: CornerCache,
    c011: CornerCache,
    c111: CornerCache,
    tx: Double,
    ty: Double,
    tz: Double
  ): BlockCache? {
    val sx = Curve.smoothstep01(tx)
    val sy = Curve.smoothstep01(ty)
    val sz = Curve.smoothstep01(tz)

    return BlockCache(
      Curve.trilerp(
        c000.warpX,
        c100.warpX,
        c010.warpX,
        c110.warpX,
        c001.warpX,
        c101.warpX,
        c011.warpX,
        c111.warpX,
        sx, sy, sz
      ),
      Curve.trilerp(
        c000.warpY,
        c100.warpY,
        c010.warpY,
        c110.warpY,
        c001.warpY,
        c101.warpY,
        c011.warpY,
        c111.warpY,
        sx, sy, sz
      ),
      Curve.trilerp(
        c000.warpZ,
        c100.warpZ,
        c010.warpZ,
        c110.warpZ,
        c001.warpZ,
        c101.warpZ,
        c011.warpZ,
        c111.warpZ,
        sx, sy, sz
      )
    )
  }

  override fun carveBlocks(
    ctx: GenerateContext,
    cave: CaveContext,
    cache: BlockCache?
  ): Double {
    if(cache == null) return 0.0
    val solidDensity = max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0

    val x = cave.worldX
    val y = cave.y
    val z = cave.worldZ

    val wx = x + cache.warpX
    val wy = y + cache.warpY
    val wz = z + cache.warpZ

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
}
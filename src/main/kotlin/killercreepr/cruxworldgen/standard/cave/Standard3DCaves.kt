package killercreepr.cruxworldgen.standard.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.util.Curve
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
) : CaveType.HasSurfaceOpenings<Standard3DCaves.CornerCache, Standard3DCaves.BlockCache>, Noised {

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

  override fun carveBlocks(ctx: GenerateContext, cave: CaveContext, cache: BlockCache?): Double {
    if(cache == null) return 0.0
    val solidDensity = max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0

    val x = cave.worldX
    val y = cave.y
    val z = cave.worldZ

    val carveNoise = ctx.noise.get(noise.Carve3D)

    val wx = x + cache.warpX
    val wy = y + cache.warpY
    val wz = z + cache.warpZ

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
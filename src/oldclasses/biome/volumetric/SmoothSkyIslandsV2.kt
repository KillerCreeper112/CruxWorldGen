package killercreepr.cruxworldgen.test.biome.volumetric

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.biome.volumetric.VolumetricBiome
import killercreepr.cruxworldgen.api.biome.volumetric.VolumetricBiomeShape
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.MaterialContext
import killercreepr.cruxworldgen.api.context.volumetric.VolumeEnv
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.material.MaterialProvider
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.signal.SignalWriter
import killercreepr.cruxworldgen.bukkit.biome.BukkitBiome
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import killercreepr.cruxworldgen.core.feature.HeightFilter
import kotlin.math.abs
import kotlin.math.pow

/**
 * SmoothSkyIslandsV2
 *
 * Goals:
 * - Shows up reliably (high baseline suitability inside sky belt)
 * - Looks OK with coarse 3D sampling (4/8 cell size): no hard-kill, wide gates
 * - Avoids huge discontinuities (no -9999, no null density)
 *
 * If you want MORE islands: lower `maskThreshold` or increase `maskSoftness` and `halfHeight`.
 * If islands are too blobby: raise `bodyThreshold` and lower `bodySoftness`.
 */
class SmoothSkyIslandsV2(
  val yRange: HeightFilter,

  // Vertical belt (relative to surface)
  val centerAboveSurface: Double = 112.0,
  val halfHeight: Double = 95.0,          // wide belt => fewer “bands” with coarse sampling

  // Suitability / coverage
  val maskThreshold: Double = 0.20,       // low => common
  val maskSoftness: Double = 0.22,        // wide => smooth

  // Body shaping (wide gates to reduce plateaus)
  val bodyThreshold: Double = 0.46,
  val bodySoftness: Double = 0.20,
  val coreThreshold: Double = 0.60,
  val coreSoftness: Double = 0.16,

  // Strength (make sure it actually turns solid)
  val bodyStrength: Double = 175.0,
  val coreStrength: Double = 120.0,

  // Voids/detail (kept gentle)
  val voidThreshold: Double = 0.72,
  val voidSoftness: Double = 0.18,
  val voidStrength: Double = 55.0,
  val edgeFoldAmp: Double = 8.0,

  // Warp (small enough for coarse sampling)
  val warpAmp: Double = 14.0,
  val yScale: Double = 0.75
) : VolumetricBiome, Noised, BukkitBiome {

  interface Noise {
    val mask3D: NoiseKey
    val body3D: NoiseKey
    val warp3D: NoiseKey
  }

  object DefaultNoise : NoiseModule, Noise {
    override val mask3D = object : NoiseKey { override val id = "biome3D.smooth_sky_islands_v2.mask3D" }
    override val body3D = object : NoiseKey { override val id = "biome3D.smooth_sky_islands_v2.body3D" }
    override val warp3D = object : NoiseKey { override val id = "biome3D.smooth_sky_islands_v2.warp3D" }

    override fun install(bank: NoiseBank) {
      bank.register(mask3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0014)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
      bank.register(body3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0021)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(3)
        }
      }
      bank.register(warp3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0010)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(1)
        }
      }
    }
  }

  override val noiseModule: NoiseModule = DefaultNoise

  override val materialProvider: MaterialProvider = object : MaterialProvider {
    private val END_STONE = BukkitBlockResolver.INSTANCE.resolve("end_stone")
    override fun chooseMaterial(context: MaterialContext): BlockData {
      return if (context.isSolid) END_STONE else BlockData.NONE
    }
  }

  private fun smoothstep01(t: Double): Double = t * t * (3.0 - 2.0 * t)

  /** Soft gate centered on threshold with adjustable softness width. */
  private fun gate01(x01: Double, threshold: Double, softness: Double): Double {
    val a = (threshold - softness).coerceIn(0.0, 1.0)
    val b = (threshold + softness).coerceIn(0.0, 1.0)
    val u = ((x01 - a) / (b - a)).coerceIn(0.0, 1.0)
    return smoothstep01(u)
  }

  private fun skyBeltMask(env: VolumeEnv): Double {
    val h = env.heightAboveSurface.toDouble()
    val t = ((halfHeight - abs(h - centerAboveSurface)) / halfHeight).coerceIn(0.0, 1.0)
    return smoothstep01(t).pow(1.25)
  }

  override fun suitability(
    ctx: GenerateContext,
    worldX: Int,
    y: Int,
    worldZ: Int,
    env: VolumeEnv,
    signals: SignalWriter
  ): Double {
    if (!yRange.isWithinRange(ctx, y)) return 0.0

    val belt = skyBeltMask(env)
    if (belt <= 1e-6) return 0.0

    val maskN = ctx.noise.get((DefaultNoise as Noise).mask3D)
    val raw = maskN.noise3D(worldX, y, worldZ) * 0.5 + 0.5

    // NOTE: belt provides a strong baseline so it “exists” reliably,
    // mask decides clustering inside that belt.
    val cluster = gate01(raw, maskThreshold, maskSoftness).pow(1.1)

    return (belt * cluster).coerceIn(0.0, 1.0)
  }

  override val shape = object : VolumetricBiomeShape {
    override fun density(
      ctx: GenerateContext,
      worldX: Int,
      y: Int,
      worldZ: Int,
      env: VolumeEnv,
      signals: SignalWriter
    ): DensityStack? {
      val belt = skyBeltMask(env)
      if (belt <= 1e-6) return DensityStack.densityStack(0.0, 0.0, 0.0)

      val noiseKeys = (DefaultNoise as Noise)
      val maskN = ctx.noise.get(noiseKeys.mask3D)
      val bodyN = ctx.noise.get(noiseKeys.body3D)
      val warpN = ctx.noise.get(noiseKeys.warp3D)

      // Gentle domain warp (coarse-sampling friendly)
      val wx = worldX + warpN.noise3D(worldX, y, worldZ) * warpAmp
      val wy = y + warpN.noise3D(worldX + 911, y - 203, worldZ + 337) * (warpAmp * 0.35)
      val wz = worldZ + warpN.noise3D(worldX - 407, y + 119, worldZ - 673) * warpAmp

      // Cluster mask (same logic as suitability, but re-evaluated for density stability)
      val mask01 = (maskN.noise3D(wx, wy, wz) * 0.5 + 0.5).coerceIn(0.0, 1.0)
      val cluster = gate01(mask01, maskThreshold, maskSoftness).pow(1.15)
      if (cluster <= 1e-6) return DensityStack.densityStack(0.0, 0.0, 0.0)

      // Body field
      val body01 = (bodyN.noise3D(wx, wy * yScale, wz) * 0.5 + 0.5).coerceIn(0.0, 1.0)
      val bodyMask = gate01(body01, bodyThreshold, bodySoftness)
      val coreMask = gate01(body01, coreThreshold, coreSoftness).pow(1.35)

      // Main mass
      var add = (bodyMask * bodyStrength + coreMask * coreStrength) * belt * cluster

      // Soft voids
      val void01 = (maskN.noise3D(wx + 1200.0, wy * 0.85 - 700.0, wz - 1600.0) * 0.5 + 0.5).coerceIn(0.0, 1.0)
      val voidMask = gate01(void01, voidThreshold, voidSoftness).pow(1.9)

      val edge01 = (1.0 - coreMask).coerceIn(0.0, 1.0)
      val underside01 = ((centerAboveSurface - env.heightAboveSurface.toDouble()) / halfHeight).coerceIn(0.0, 1.0)
      val undersideBias = 1.0 + underside01 * 0.55

      add -= voidMask * voidStrength * belt * cluster * undersideBias * (0.35 + 0.65 * edge01)

      // Gentle wrinkles
      val fold01 = (bodyN.noise3D(wx - 2000.0, wy * 0.35 + 999.0, wz + 2000.0) * 0.5 + 0.5).coerceIn(0.0, 1.0)
      val fold = (fold01 - 0.5) * edgeFoldAmp
      add += fold * belt * cluster * (0.25 + 0.75 * edge01)

      // Very gentle top taper
      val h = env.heightAboveSurface.toDouble()
      val topness = ((h - centerAboveSurface) / halfHeight).coerceIn(-1.0, 1.0)
      add -= topness.coerceAtLeast(0.0) * 6.0 * belt * cluster

      return DensityStack.densityStack(base = 0.0, add = add * 300, carve = 0.0)
    }
  }

  // Adjust to whatever Bukkit biome you want to report
  override fun toBukkitBiome(): org.bukkit.block.Biome = org.bukkit.block.Biome.THE_END
}
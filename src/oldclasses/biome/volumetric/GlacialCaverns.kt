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
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import killercreepr.cruxworldgen.bukkit.biome.BukkitBiome
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import killercreepr.cruxworldgen.core.feature.HeightFilter
import org.bukkit.block.Biome
import kotlin.math.abs
import kotlin.math.pow

class GlacialCaverns(
  val noise: Noise = DefaultNoise,
  val yRange: HeightFilter,
  val threshold: Double = 0.52, // ↑ rarer caverns, ↓ more common
  val sharpness: Double = 1.35  // ↑ tighter cave clusters, ↓ softer spread
) : VolumetricBiome, Noised, BukkitBiome {

  interface Noise {
    val caveMask3D: NoiseKey
    val caveBody3D: NoiseKey
    val icicle3D: NoiseKey
  }

  object DefaultNoise : NoiseModule, Noise {
    override val caveMask3D = object : NoiseKey { override val id = "biome3D.glacial_caverns.mask3D" }
    override val caveBody3D = object : NoiseKey { override val id = "biome3D.glacial_caverns.body3D" }
    override val icicle3D  = object : NoiseKey { override val id = "biome3D.glacial_caverns.icicle3D" }

    override fun install(bank: NoiseBank) {
      bank.register(caveMask3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0021)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }

      bank.register(caveBody3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0045)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(3)
        }
      }

      bank.register(icicle3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.009)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
    }
  }

  override val noiseModule = DefaultNoise

  override val materialProvider = object : MaterialProvider {
    override fun chooseMaterial(context: MaterialContext): BlockData {
      if (!context.isSolid) return BlockData.NONE

      // Keep it simple/vanilla-ish. You can swap this for a smarter provider later
      // (packed ice / blue ice / snow blocks / calcite mix, etc.)
      return BukkitBlockResolver.INSTANCE.resolve("packed_ice")
    }
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

    // Favor BELOW-surface placement (heightAboveSurface < 0)
    val h = env.heightAboveSurface.toDouble() // underground = negative
    val preferredDepth = 34.0                 // blocks below local surface
    val halfDepthBand = 30.0

    val depth = (-h) // positive when below surface
    val depthT = ((halfDepthBand - abs(depth - preferredDepth)) / halfDepthBand).coerceIn(0.0, 1.0)
    val depthMask = smoothstep01(depthT)
    if (depthMask <= 0.001) return 0.0

    // Cluster mask so not every underground region becomes glacial caverns
    val rawMask = ctx.noise.get(noise.caveMask3D).noise3D(worldX, y, worldZ) * 0.5 + 0.5
    val t = ((rawMask - threshold) / (1.0 - threshold)).coerceIn(0.0, 1.0)
    val gated = smoothstep01(t).pow(sharpness)

    return (depthMask * gated).coerceIn(0.0, 1.0)
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
      // ---- Tunables ----
      val centerDepth = 36.0          // preferred cave layer depth below local surface
      val halfDepth = 34.0            // thickness of affected underground band

      val chamberThreshold = 0.57     // controls chamber rarity
      val chamberCarveStrength = 120.0

      val tunnelThreshold = 0.63      // smaller / stringier carve detail
      val tunnelCarveStrength = 65.0

      val shellFreezeStrength = 16.0  // adds icy shell / walls around carve zones
      val icicleAddStrength = 22.0    // hanging / rising icy features

      val warpAmp = 18.0
      val yScale = 0.78

      // ---- Underground depth band relative to local surface ----
      val h = env.heightAboveSurface.toDouble()
      val depth = (-h) // positive below surface, <= 0 above surface

      val layerT = ((halfDepth - abs(depth - centerDepth)) / halfDepth).coerceIn(0.0, 1.0)
      val layerMask = smoothstep01(layerT)

      // Only underground
      if (depth <= 0.0 || layerMask <= 0.001) {
        return DensityStack.densityStack(base = 0.0, add = 0.0, carve = 0.0)
      }

      val maskN = ctx.noise.get(noise.caveMask3D)
      val bodyN = ctx.noise.get(noise.caveBody3D)
      val icicleN = ctx.noise.get(noise.icicle3D)

      // ---- Domain warp for natural cave flow ----
      val wx = worldX + maskN.noise3D(worldX, y, worldZ) * warpAmp
      val wy = y + maskN.noise3D(worldX + 913, y - 271, worldZ + 417) * (warpAmp * 0.55)
      val wz = worldZ + maskN.noise3D(worldX - 611, y + 199, worldZ + 733) * warpAmp

      // ---- Main chamber carve field ----
      val chamberRaw = (bodyN.noise3D(wx, wy * yScale, wz) * 0.5 + 0.5).coerceIn(0.0, 1.0)
      val cT = ((chamberRaw - chamberThreshold) / (1.0 - chamberThreshold)).coerceIn(0.0, 1.0)
      val chamberMask = smoothstep01(cT).pow(1.2)

      // ---- Secondary tunnel carve field ----
      val tunnelRaw = (
        bodyN.noise3D(wx + 1400.0, wy * 1.15 - 800.0, wz - 1200.0) * 0.5 + 0.5
      ).coerceIn(0.0, 1.0)
      val tT = ((tunnelRaw - tunnelThreshold) / (1.0 - tunnelThreshold)).coerceIn(0.0, 1.0)
      val tunnelMask = smoothstep01(tT).pow(2.0)

      // Combined carve (caverns + stringier tunnels)
      var carve = (chamberMask * chamberCarveStrength + tunnelMask * tunnelCarveStrength) * layerMask

      // ---- Keep some denser "floors" / "ceilings" so it's not all swiss cheese ----
      // Reduces carve near exact band center less than at edges (so still roomy inside)
      val bandEdge01 = (1.0 - layerMask).coerceIn(0.0, 1.0)
      carve *= (0.92 + 0.08 * (1.0 - bandEdge01))

      // ---- Icy shell around caves (additive solids near cave boundaries) ----
      // High at cave edges, low in wide-open cores
      val caveOpen01 = chamberMask.coerceIn(0.0, 1.0)
      val caveEdge01 = (caveOpen01 * (1.0 - caveOpen01) * 4.0).coerceIn(0.0, 1.0)
      var add = caveEdge01 * shellFreezeStrength * layerMask

      // ---- Icicle / ice spike detail ----
      // Bias stronger near ceilings and floors of the cave band
      val ceilingBias = ((depth - centerDepth) / halfDepth).coerceIn(-1.0, 1.0) // positive deeper than center
      val topBottomBias = abs(ceilingBias) // stronger toward band extremes

      val icicleRaw = (icicleN.noise3D(wx * 1.05, wy * 1.35, wz * 1.05) * 0.5 + 0.5).coerceIn(0.0, 1.0)
      val iT = ((icicleRaw - 0.68) / (1.0 - 0.68)).coerceIn(0.0, 1.0)
      val icicleMask = smoothstep01(iT).pow(2.8)

      // Put spikes mostly where caves exist, but not in the absolute centers
      val spikePlacement = (0.25 + 0.75 * caveEdge01) * (0.35 + 0.65 * topBottomBias)
      add += icicleMask * icicleAddStrength * layerMask * spikePlacement

      // ---- Optional "frozen seams" wrinkle to break smooth walls ----
      val seamRaw = (bodyN.noise3D(wx - 2200.0, wy * 0.55 + 333.0, wz + 1900.0) * 0.5 + 0.5).coerceIn(0.0, 1.0)
      val seam = (seamRaw - 0.5) * 10.0
      add += seam * layerMask * caveEdge01 * 0.65

      return DensityStack.densityStack(
        base = 0.0,
        add = add,
        carve = carve
      )
    }
  }

  override fun toBukkitBiome(): Biome = Biome.ICE_SPIKES
}
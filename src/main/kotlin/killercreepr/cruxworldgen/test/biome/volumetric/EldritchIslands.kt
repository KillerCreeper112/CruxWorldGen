package killercreepr.cruxworldgen.test.biome.volumetric

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.biome.volumetric.VolumetricBiome
import killercreepr.cruxworldgen.api.biome.volumetric.VolumetricBiomeShape
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.MaterialContext
import killercreepr.cruxworldgen.api.context.volumetric.VolumeEnv
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.material.MaterialProvider
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.signal.SignalWriter
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import killercreepr.cruxworldgen.bukkit.biome.BukkitBiome
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import killercreepr.cruxworldgen.core.feature.HeightFilter
import killercreepr.cruxworldgen.test.biome.EldritchWastes
import org.bukkit.block.Biome
import kotlin.math.pow

class EldritchIslands(
  val noise : Noise = DefaultNoise,
  val yRange : HeightFilter,
  val threshold : Double = 0.55, // ↑ rarer, ↓ more common
  val sharpness : Double = 1.5   // ↑ tighter clusters, ↓ softer spread
) : VolumetricBiome, Noised, BukkitBiome {
  interface Noise {
    val skyMask3D :NoiseKey
    val skyBody3D : NoiseKey
  }

  object DefaultNoise : NoiseModule, Noise{
    override val skyMask3D = object : NoiseKey{ override val id = "biome3D.eldritch_islands.mask3D" }
    override val skyBody3D = object : NoiseKey{ override val id = "biome3D.eldritch_islands.body3D" }

    override fun install(bank: NoiseBank) {
      bank.register(skyMask3D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.0018)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
      bank.register(skyBody3D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.002)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(3)
        }
      }
    }
  }

  override fun allowedIn(surface: BiomeBlendSample): Boolean = surface.primaryBiome() is EldritchWastes

  override val noiseModule = DefaultNoise

  override val materialProvider = object : MaterialProvider {
    override fun chooseMaterial(context: MaterialContext): BlockData {
      if(context.isSolid) return BukkitBlockResolver.INSTANCE.resolve("end_stone")
      return BlockData.NONE
    }
  }

  override fun suitability(ctx: GenerateContext, worldX: Int, y: Int, worldZ: Int, env: VolumeEnv, signals: SignalWriter): Double {
    if (!yRange.isWithinRange(ctx, y)) return 0.0

    val rawMask = ctx.noise.get(noise.skyMask3D).noise3D(worldX, y, worldZ) * 0.5 + 0.5

    val t = ((rawMask - threshold) / (1.0 - threshold)).coerceIn(0.0, 1.0)
    val gatedMask = (t * t * (3.0 - 2.0 * t)).pow(sharpness)

    return (gatedMask).coerceIn(0.0, 1.0)
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
      // Tunables
      val centerAboveSurface = 115.0
      val halfHeight = 58.0

      val bodyThreshold = 0.56
      val coreThreshold = 0.66
      val bodyStrength = 120.0
      val coreStrength = 70.0

      val voidThreshold = 0.74
      val voidStrength = 70.0
      val undersideBiteBoost = 1.45

      val warpAmp = 26.0
      val yScale = 0.62

      // Relative sky belt (more stable over varying terrain)
      val h = env.heightAboveSurface.toDouble()
      val layerT = ((halfHeight - kotlin.math.abs(h - centerAboveSurface)) / halfHeight).coerceIn(0.0, 1.0)
      val layerMask = smoothstep01(layerT)
      if (layerMask <= 0.001) return DensityStack.densityStack(base = 0.0, add = -9999.0, carve = 0.0)

      val maskN = ctx.noise.get(noise.skyMask3D)
      val bodyN = ctx.noise.get(noise.skyBody3D)

      // Domain warp
      val wx = worldX + maskN.noise3D(worldX, y, worldZ) * warpAmp
      val wy = y + maskN.noise3D(worldX + 777, y - 333, worldZ + 111) * (warpAmp * 0.45)
      val wz = worldZ + maskN.noise3D(worldX - 444, y + 222, worldZ + 999) * warpAmp

      // Macro body
      val bodyRaw = (bodyN.noise3D(wx, wy * yScale, wz) * 0.5 + 0.5).coerceIn(0.0, 1.0)

      val bt = ((bodyRaw - bodyThreshold) / (1.0 - bodyThreshold)).coerceIn(0.0, 1.0)
      val bodyMask = smoothstep01(bt) // soft shell

      val ct = ((bodyRaw - coreThreshold) / (1.0 - coreThreshold)).coerceIn(0.0, 1.0)
      val coreMask = smoothstep01(ct).pow(1.6) // denser core, survives coarse blending

      // Core + shell mass (blend-friendly)
      var add = (bodyMask * bodyStrength + coreMask * coreStrength) * layerMask

      // Void carving (more at edges / undersides than in core)
      val voidRaw = (maskN.noise3D(wx + 1300.0, wy * 0.9 - 800.0, wz - 1700.0) * 0.5 + 0.5).coerceIn(0.0, 1.0)
      val vt = ((voidRaw - voidThreshold) / (1.0 - voidThreshold)).coerceIn(0.0, 1.0)
      val voidMask = smoothstep01(vt).pow(2.4)

      val underside01 = ((centerAboveSurface - h) / halfHeight).coerceIn(0.0, 1.0)
      val undersideBias = 1.0 + underside01 * (undersideBiteBoost - 1.0)

      val edge01 = (1.0 - coreMask).coerceIn(0.0, 1.0)
      add -= voidMask * voidStrength * layerMask * undersideBias * (0.35 + 0.65 * edge01)

      // Fold / wrinkle detail (small amplitude, mostly near edges)
      val foldRaw = (bodyN.noise3D(wx - 2100.0, wy * 0.35 + 999.0, wz + 2100.0) * 0.5 + 0.5).coerceIn(0.0, 1.0)
      val fold = (foldRaw - 0.5) * 16.0
      add += fold * layerMask * (0.25 + 0.75 * edge01)

      // Slight downward taper to reduce slab tops
      val topness = ((h - centerAboveSurface) / halfHeight).coerceIn(-1.0, 1.0)
      add -= topness.coerceAtLeast(0.0) * 10.0 * layerMask

      return DensityStack.densityStack(base = 0.0, add = add, carve = 0.0)
    }
  }

  /*override val shape = object : VolumetricBiomeShape {
    override fun density(
      ctx: GenerateContext,
      worldX: Int,
      y: Int,
      worldZ: Int,
      env: VolumeEnv,
      signals: SignalWriter
    ): DensityStack? {
      // --- Tunables (move to constructor later if you like) ---
      val islandCenterY = 190.0         // preferred island layer height (absolute Y) if yRange is broad
      val islandHalfHeight = 42.0       // vertical layer thickness
      val bodyThreshold = 0.60          // lower = fatter islands
      val bodyStrength = 170.0          // overall mass strength

      val voidThreshold = 0.72          // lower = more hollow/missing chunks
      val voidStrength = 90.0           // how much to carve out
      val undersideBiteBoost = 1.35     // stronger carving on undersides

      val warpAmp = 28.0                // domain warp amount in blocks
      val yScale = 0.62                 // anisotropic body sampling for shelf-ish islands

      // --- Vertical layer preference (keeps islands in a coherent sky belt) ---
      val dyLayer = kotlin.math.abs(y.toDouble() - islandCenterY)
      val layerT = ((islandHalfHeight - dyLayer) / islandHalfHeight).coerceIn(0.0, 1.0)
      val layerMask = smoothstep01(layerT)
      if (layerMask <= 0.001) return DensityStack.densityStack(base = 0.0, add = -9999.0, carve = 0.0)

      // --- Simple domain warp (reuse existing mask/body noise if you don’t want a new key yet) ---
      val warpN = ctx.noise.get(noise.skyMask3D)
      val wx = worldX + warpN.noise3D(worldX, y, worldZ) * warpAmp
      val wy = y + warpN.noise3D(worldX + 777, y - 333, worldZ + 111) * (warpAmp * 0.45)
      val wz = worldZ + warpN.noise3D(worldX - 444, y + 222, worldZ + 999) * warpAmp

      // --- Body mass ---
      val bodyRaw = ctx.noise.get(noise.skyBody3D).noise3D(wx, wy * yScale, wz) * 0.5 + 0.5
      val bt = ((bodyRaw - bodyThreshold) / (1.0 - bodyThreshold)).coerceIn(0.0, 1.0)
      val bodyMask = smoothstep01(bt).pow(2.1)

      // Make islands taper near top/bottom of the sky layer
      var add = bodyMask * bodyStrength * layerMask

      // --- Eldritch "missing chunks" carve ---
      // Reusing skyMask3D as a stand-in void field for now. Better: add a dedicated skyVoid3D key.
      val voidRaw = ctx.noise.get(noise.skyMask3D).noise3D(wx + 1300.0, wy * 0.9 - 800.0, wz - 1700.0) * 0.5 + 0.5
      val vt = ((voidRaw - voidThreshold) / (1.0 - voidThreshold)).coerceIn(0.0, 1.0)
      val voidMask = smoothstep01(vt).pow(2.8)

      // Stronger bites on undersides to make hanging/undercut silhouettes
      val underside01 = ((islandCenterY - y.toDouble()) / islandHalfHeight).coerceIn(0.0, 1.0)
      val undersideBias = 1.0 + underside01 * (undersideBiteBoost - 1.0)

      add -= voidMask * voidStrength * layerMask * undersideBias

      // Extra subtle vertical breakup so islands feel "folded"
      val foldRaw = ctx.noise.get(noise.skyBody3D).noise3D(wx - 2100.0, wy * 0.35 + 999.0, wz + 2100.0) * 0.5 + 0.5
      val fold = (foldRaw - 0.5) * 22.0
      add += fold * layerMask * 0.35

      return DensityStack.densityStack(base = 0.0, add = add, carve = 0.0)
    }
  }*/

  override fun toBukkitBiome(): Biome = Biome.BASALT_DELTAS
}
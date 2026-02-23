package killercreepr.cruxworldgen.test.biome.volumetric

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.biome.volumetric.VolumetricBiome
import killercreepr.cruxworldgen.api.biome.volumetric.VolumetricBiomeShape
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.MaterialContext
import killercreepr.cruxworldgen.api.context.volumetric.VolumeEnv
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.material.MaterialProvider
import killercreepr.cruxworldgen.api.noise.NoiseBank
import killercreepr.cruxworldgen.api.noise.NoiseField
import killercreepr.cruxworldgen.api.noise.NoiseKey
import killercreepr.cruxworldgen.api.noise.NoiseModule
import killercreepr.cruxworldgen.api.noise.Noised
import killercreepr.cruxworldgen.api.signal.SignalWriter
import killercreepr.cruxworldgen.bukkit.biome.BukkitBiome
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import killercreepr.cruxworldgen.core.feature.PlacedFeature
import killercreepr.cruxworldgen.core.feature.diamondSkyIslands
import killercreepr.cruxworldgen.core.feature.ironHigh
import killercreepr.cruxworldgen.core.feature.ironLow
import org.bukkit.block.Biome

class SkyIslands : VolumetricBiome, Noised, BukkitBiome {
  object Noise : NoiseModule{
    object SkyMask3D : NoiseKey{ override val id = "biome3D.sky_islands.mask3D" }
    object SkyBody3D : NoiseKey{ override val id = "biome3D.sky_islands.body3D" }

    override fun install(bank: NoiseBank) {
      bank.register(SkyMask3D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.0018)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
      bank.register(SkyBody3D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.0028)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(3)
        }
      }
    }
  }
  override val noiseModule = Noise

  override val materialProvider = object : MaterialProvider {
    override fun chooseMaterial(context: MaterialContext): BlockData {
      if(context.isSolid) return BukkitBlockResolver.INSTANCE.resolve("end_stone")
      return BlockData.NONE
    }
  }
  override val features: List<PlacedFeature<*>> = listOf(
    diamondSkyIslands
  )

  override fun suitability(ctx: GenerateContext, worldX: Int, y: Int, worldZ: Int, env: VolumeEnv, signals: SignalWriter): Double {
    val h = env.heightAboveSurface
    if (h !in 90..180) return 0.0
    val band = (1.0 - kotlin.math.abs(h - 115.0) / 65.0).coerceIn(0.0, 1.0)
    val mask = ctx.noise.get(Noise.SkyMask3D).noise3D(worldX, y, worldZ) * 0.5 + 0.5
    return (band * mask).coerceIn(0.0, 1.0)
  }

  override val shape = object : VolumetricBiomeShape{
    override fun density(
      ctx: GenerateContext,
      worldX: Int,
      y: Int,
      worldZ: Int,
      edge: BiomeEdgeContext,
      signalWriter: SignalWriter
    ): DensityStack  = DensityStack.densityStack(0.0,0.0,0.0)

    /** Optional density influence (for sky islands etc). Return null = no effect. */
    override fun density(
      ctx: GenerateContext,
      worldX: Int,
      y: Int,
      worldZ: Int,
      env: VolumeEnv,
      signals: SignalWriter
    ): DensityStack? {
      val n = ctx.noise.get(Noise.SkyBody3D).noise3D(worldX, y, worldZ) * 0.5 + 0.5
      val add = (n - 0.62) * 200.6
      return DensityStack.densityStack(base = 0.0, add = add, carve = 0.0)
    }
  }

  override fun toBukkitBiome(): Biome = Biome.NETHER_WASTES
}
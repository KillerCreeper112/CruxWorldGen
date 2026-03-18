package killercreepr.cruxworldgen.test.biome.volumetric

import killercreepr.crux.core.Crux
import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.biome.volumetric.VolumetricBiome
import killercreepr.cruxworldgen.api.biome.volumetric.VolumetricBiomeShape
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.MaterialContext
import killercreepr.cruxworldgen.api.context.volumetric.VolumeEnv
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.density.VolDensityStack
import killercreepr.cruxworldgen.api.feature.HeightFilter
import killercreepr.cruxworldgen.api.material.MaterialProvider
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.signal.SignalWriter
import killercreepr.cruxworldgen.api.util.Curve
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import killercreepr.cruxworldgen.bukkit.biome.BukkitBiome
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import org.bukkit.block.Biome
import kotlin.math.abs
import kotlin.math.pow

class GlacialCaverns(
  val noise: Noise = DefaultNoise,
  val yRange: HeightFilter,
) : VolumetricBiome, Noised, BukkitBiome {

  interface Noise {
    val biomeMask3D: NoiseKey
    val cavern3D: NoiseKey
    val tunnel3D: NoiseKey
    val detail3D: NoiseKey
    val icicle3D: NoiseKey
    val warpX3D: NoiseKey
    val warpY3D: NoiseKey
    val warpZ3D: NoiseKey
    val shelf3D: NoiseKey
    val pillar3D: NoiseKey
  }

  object DefaultNoise : NoiseModule, Noise {
    override val biomeMask3D = object : NoiseKey { override val id = "biome3D.glacial_caverns.mask3D" }
    override val cavern3D   = object : NoiseKey { override val id = "biome3D.glacial_caverns.cavern3D" }
    override val tunnel3D   = object : NoiseKey { override val id = "biome3D.glacial_caverns.tunnel3D" }
    override val detail3D   = object : NoiseKey { override val id = "biome3D.glacial_caverns.detail3D" }
    override val icicle3D   = object : NoiseKey { override val id = "biome3D.glacial_caverns.icicle3D" }
    override val warpX3D    = object : NoiseKey { override val id = "biome3D.glacial_caverns.warpX3D" }
    override val warpY3D    = object : NoiseKey { override val id = "biome3D.glacial_caverns.warpY3D" }
    override val warpZ3D    = object : NoiseKey { override val id = "biome3D.glacial_caverns.warpZ3D" }
    override val shelf3D    = object : NoiseKey { override val id = "biome3D.glacial_caverns.shelf3D" }
    override val pillar3D    = object : NoiseKey { override val id = "biome3D.glacial_caverns.pillar3D" }

    override fun install(bank: NoiseBank) {
      bank.register(shelf3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0025)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
      bank.register(pillar3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.002)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
      bank.register(biomeMask3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0007)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      bank.register(cavern3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0042)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.PingPong)
          rotationType3D(CruxNoise.RotationType3D.ImproveXZPlanes)
          fractalOctaves(3)
        }
      }

      bank.register(tunnel3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0075)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      bank.register(detail3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.011)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      bank.register(icicle3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.010)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      bank.register(warpX3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0050)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      bank.register(warpY3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0050)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      bank.register(warpZ3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0050)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
    }
  }

  override val noiseModule = DefaultNoise

  override val materialProvider = object : MaterialProvider {
    override fun chooseMaterial(context: MaterialContext): BlockData {
      if (!context.isSolid) return BlockData.NONE
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

    val depthBelowSurface = (-env.heightAboveSurface.toDouble()).coerceAtLeast(0.0)
    if (depthBelowSurface <= 0.0) return 0.0

    val noise = ctx.noise.get(noise.biomeMask3D).noise3D(worldX, y, worldZ)
    return noise
  }

  override val shape = object : VolumetricBiomeShape {
    override fun density(
      ctx: GenerateContext,
      worldX: Int,
      y: Int,
      worldZ: Int,
      env: VolumeEnv,
      signals: SignalWriter
    ): VolDensityStack? {
      if (!yRange.isWithinRange(ctx, y)) return VolDensityStack.emptyStack()

      val depthBelowSurface = (-env.heightAboveSurface.toDouble()).coerceAtLeast(0.0)
      if (depthBelowSurface <= 0.0) return VolDensityStack.emptyStack()

      val cavern = ctx.noise.get(noise.cavern3D).noise3D(worldX, y, worldZ)
      val shelf = ctx.noise.get(noise.shelf3D).noise3D(worldX, y, worldZ)


      val base = shelf * 5.0
      val carve = cavern * 2.5
      return VolDensityStack.volDensityStack(
        base = base,
        carve = carve,
        add = 0.0,
        replaceMask = 1.0
      )
    }
  }

  override fun toBukkitBiome(): Biome = Biome.ICE_SPIKES
}
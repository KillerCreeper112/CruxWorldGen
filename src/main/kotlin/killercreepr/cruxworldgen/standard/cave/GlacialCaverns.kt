package killercreepr.cruxworldgen.test.biome.volumetric

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.biome.volumetric.VolumetricBiome
import killercreepr.cruxworldgen.api.biome.volumetric.VolumetricBiomeShape
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.block.BlockPicker
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.MaterialContext
import killercreepr.cruxworldgen.api.context.volumetric.VolumeEnv
import killercreepr.cruxworldgen.api.density.VolDensityStack
import killercreepr.cruxworldgen.api.feature.HeightFilter
import killercreepr.cruxworldgen.api.material.MaterialProvider
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.signal.SignalWriter
import killercreepr.cruxworldgen.api.util.Curve
import killercreepr.cruxworldgen.bukkit.biome.BukkitBiome
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockAdapter
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import killercreepr.cruxworldgen.extension.remap01
import killercreepr.cruxworldgen.standard.decor.volumetric.IcicleVolDecor
import org.bukkit.Material
import org.bukkit.block.Biome
import kotlin.math.max

class GlacialCaverns(
  val noise: Noise = DefaultNoise,
  val yRange: HeightFilter,
) : VolumetricBiome, Noised, BukkitBiome {

  override val volumetricDecorations = listOf(
    IcicleVolDecor(
      block = BlockPicker.constant(BukkitBlockAdapter.resolver().resolve(Material.ICE)),
      yOffset = 1
    ),
    IcicleVolDecor(
      block = BlockPicker.constant(BukkitBlockAdapter.resolver().resolve(Material.ICE)),
      yOffset = 1
    )
  )

  interface Noise {
    val biomeMask3D: NoiseKey
    val surface2D: NoiseKey
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
    override val surface2D = object : NoiseKey { override val id = "biome3D.glacial_caverns.surface2D" }
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
      bank.register(surface2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.002)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
      bank.register(shelf3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.001)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          rotationType3D(CruxNoise.RotationType3D.ImproveXZPlanes)
          fractalOctaves(1)
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
          frequency(0.0025)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      bank.register(cavern3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.006)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          rotationType3D(CruxNoise.RotationType3D.ImproveXZPlanes)
          fractalOctaves(2)
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

    val maskRaw = ctx.noise.get(noise.biomeMask3D).noise3D(worldX, y, worldZ).remap01()
    val depthMask = Curve.smoothstep(4.0, 18.0, depthBelowSurface)

    return Curve.smoothstep(0.55, 0.75, maskRaw) * depthMask
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

      val x = worldX
      val z = worldZ

      val suitability = suitability(ctx, x, y, z, env, signals)
      if (suitability <= 0.0) return VolDensityStack.emptyStack()

      val solidDensity = max(0.0, env.terrainDensity)

      val wx = x + (ctx.noise.get(noise.warpX3D).noise3D(x,y,z) * 70.0)
      val wy = y + (ctx.noise.get(noise.warpY3D).noise3D(x,y,z) * 10.0)
      val wz = z + (ctx.noise.get(noise.warpZ3D).noise3D(x,y,z) * 70.0)

      val raw = ctx.noise.get(noise.cavern3D).noise3D(wx, wy, wz)

      val threshold = 0.3
      val ramp = 0.5
      val startRaw = threshold * 2.0 - 1.0
      val endRaw = (threshold + ramp) * 2.0 - 1.0

      if (raw <= startRaw) return VolDensityStack.emptyStack()

// Full carve strength — suitability only gates the edge via the early return above
      val carveStrength = solidDensity + 0.8
      val shelf = ctx.noise.get(noise.shelf3D).noise3D(x * 0.8, y * 0.6, z * 0.8) * 12.0

// Suitability thins out the edges by scaling the final output,
// but doesn't interfere with whether a cave forms at all
      val edgeFade = Curve.smoothstep(0.15, 0.6, suitability)

      if (raw >= endRaw) return VolDensityStack.volDensityStack(
        add = shelf * edgeFade,
        carve = carveStrength * edgeFade
      )

      val carveN = (raw + 1.0) * 0.5
      val carve = Curve.smoothstep(threshold, threshold + ramp, carveN)

      val baseVariation = ctx.noise.get(noise.detail3D).noise3D(x * 0.5, y * 0.2, z * 0.5) * 0.1
      return VolDensityStack.volDensityStack(
        base = baseVariation * edgeFade,
        carve = carve * carveStrength * edgeFade,
        add = shelf * edgeFade,
        replaceMask = 0.0
      )
    }
  }

  override fun toBukkitBiome(): Biome = Biome.ICE_SPIKES
}
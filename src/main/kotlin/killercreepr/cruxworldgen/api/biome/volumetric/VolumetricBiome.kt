package killercreepr.cruxworldgen.api.biome.volumetric

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.biome.BiomeShape
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.volumetric.VolumeEnv
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.material.MaterialProvider
import killercreepr.cruxworldgen.api.signal.SignalWriter

interface VolumetricBiome : Biome {
  /** 0..1 “how much do I want to exist here” (not normalized). */
  fun suitability(
    ctx: GenerateContext,
    worldX: Int, y: Int, worldZ: Int,
    env: VolumeEnv,
    signals: SignalWriter
  ): Double

  override val shape: VolumetricBiomeShape
}
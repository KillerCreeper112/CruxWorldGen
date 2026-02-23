package killercreepr.cruxworldgen.api.biome.volumetric

import killercreepr.cruxworldgen.api.biome.BiomeShape
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.volumetric.VolumeEnv
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.signal.SignalWriter

interface VolumetricBiomeShape : BiomeShape {
  /** Optional density influence (for sky islands etc). Return null = no effect. */
  fun density(
    ctx: GenerateContext,
    worldX: Int, y: Int, worldZ: Int,
    env: VolumeEnv,
    signals: SignalWriter
  ): DensityStack? = null

  /** Optional extra cave carving influence. */
  /*fun carve(
    ctx: GenerateContext,
    worldX: Int, y: Int, worldZ: Int,
    env: VolumeEnv,
    signals: SignalWriter
  ): Double = 0.0

  *//** Optional extra “additive” solids influence. *//*
  fun add(
    ctx: GenerateContext,
    worldX: Int, y: Int, worldZ: Int,
    env: VolumeEnv,
    signals: SignalWriter
  ): Double = 0.0*/
}
package killercreepr.cruxworldgen.api.biome.volumetric

import killercreepr.cruxworldgen.api.biome.BiomeShape
import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.volumetric.VolumeEnv
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.density.VolDensityStack
import killercreepr.cruxworldgen.api.signal.SignalWriter

interface VolumetricBiomeShape : BiomeShape {
  /** Optional density influence (for sky islands etc). Return null = no effect. */
  fun density(
    ctx: GenerateContext,
    worldX: Int, y: Int, worldZ: Int,
    env: VolumeEnv,
    signals: SignalWriter
  ): VolDensityStack? = null

  override fun density(
    ctx: GenerateContext,
    worldX: Int,
    y: Int,
    worldZ: Int,
    edge: BiomeEdgeContext,
    signalWriter: SignalWriter
  ): DensityStack = DensityStack.densityStack(0.0,0.0,0.0)

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
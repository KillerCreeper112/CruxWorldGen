package killercreepr.cruxworldgen.api.biome

import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.volumetric.VolumeEnv
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.signal.SignalWriter

interface FineBiomeShape {
  fun density(
    ctx: GenerateContext,
    worldX: Int,
    y: Int,
    worldZ: Int,
    edge: BiomeEdgeContext,
    volCtx: VolumeEnv,
    signalWriter : SignalWriter
  ): DensityStack
}
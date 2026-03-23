package killercreepr.cruxworldgen.api.biome.volumetric

import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.volumetric.VolumeEnv
import killercreepr.cruxworldgen.api.density.VolDensityBank
import killercreepr.cruxworldgen.api.density.VolDensityStack
import killercreepr.cruxworldgen.api.signal.SignalWriter

interface VolBiomeShapeType {
  fun density(
    ctx: GenerateContext,
    worldX: Int,
    y: Int,
    worldZ: Int,
    env: VolumeEnv,
    signalWriter: SignalWriter,
    baseStack : VolDensityStack,
    out : VolDensityBank
  )
}
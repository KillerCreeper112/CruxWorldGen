package killercreepr.cruxworldgen.api.biome.volumetric

import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.volumetric.VolumeEnv
import killercreepr.cruxworldgen.api.density.VolDensityStack
import killercreepr.cruxworldgen.api.signal.SignalWriter

open class VolBiomeShapeProfile(
  val base : VolumetricBiomeShape,
  val types : List<VolBiomeShapeType>
) : VolumetricBiomeShape {

  override fun density(
    ctx: GenerateContext,
    worldX: Int,
    y: Int,
    worldZ: Int,
    env: VolumeEnv,
    signals: SignalWriter
  ): VolDensityStack? {
    val baseStack = base.density(ctx, worldX, y, worldZ, env, signals) ?: VolDensityStack.emptyStack()
    val out = baseStack.toBank()
    for (shape in types) {
      shape.density(
        ctx, worldX, y, worldZ,
        env, signals, baseStack,
        out
      )
    }
    return out.toStack()
  }
}
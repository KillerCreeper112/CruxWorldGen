package killercreepr.cruxworldgen.api.biome

import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.signal.SignalWriter

open class BiomeShapeProfile(
  val base : BiomeShape,
  val types : List<BiomeShapeType>
) : BiomeShape {
  override fun density(
    ctx: GenerateContext,
    worldX: Int,
    y: Int,
    worldZ: Int,
    edge: BiomeEdgeContext,
    signalWriter: SignalWriter
  ): DensityStack {
    val baseStack = base.density(ctx, worldX, y, worldZ, edge, signalWriter)
    val out = baseStack.toBank()
    for (shape in types) {
      shape.density(
        ctx, worldX, y, worldZ,
        edge, signalWriter, baseStack,
        out
      )
    }
    return out.toStack()
  }
}
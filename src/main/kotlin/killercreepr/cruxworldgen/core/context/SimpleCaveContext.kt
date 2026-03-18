package killercreepr.cruxworldgen.core.context

import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.signal.SignalWriter

open class SimpleCaveContext(
  override var worldX: Int = 0,
  override var y: Int = 0,
  override var worldZ: Int = 0,
  override var surfaceY: Int = 0,
  override var depthBelowSurface: Int = 0,
  override var terrainDensity: Double = 0.0,
  override var edge: BiomeEdgeContext,
  override var signalWriter : SignalWriter
) : CaveContext {
}
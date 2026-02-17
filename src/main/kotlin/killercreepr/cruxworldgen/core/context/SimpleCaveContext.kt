package killercreepr.cruxworldgen.core.context

import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.signal.SignalWriter

open class SimpleCaveContext(
  override val worldX: Int,
  override val y: Int,
  override val worldZ: Int,
  override val surfaceY: Int,
  override val depthBelowSurface: Int,
  override val terrainDensity: Double,
  override val edge: BiomeEdgeContext,
  override val signalWriter : SignalWriter
) : CaveContext {
}
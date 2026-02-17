package killercreepr.cruxworldgen.api.context

import killercreepr.cruxworldgen.api.signal.SignalWriter

interface CaveContext {
  val worldX: Int
  val y: Int
  val worldZ: Int
  val surfaceY: Int
  val depthBelowSurface: Int
  val terrainDensity: Double // terrain-only density at this voxel (already blended)
  val edge: BiomeEdgeContext
  val signalWriter : SignalWriter
}
package killercreepr.cruxworldgen.test6.pillar

import killercreepr.cruxworldgen.api.context.BiomeEdgeContext

data class PillarContext(
  val worldX: Int,
  val y: Int,
  val worldZ: Int,
  val surfaceY: Int,
  val depthBelowSurface: Int,
  val terrainDensity: Double,
  val edge: BiomeEdgeContext
)

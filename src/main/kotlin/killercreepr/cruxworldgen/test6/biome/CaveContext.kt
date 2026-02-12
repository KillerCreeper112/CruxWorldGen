package killercreepr.cruxworldgen.test6.biome

data class CaveContext(
  val worldX: Int,
  val y: Int,
  val worldZ: Int,
  val surfaceY: Int,
  val depthBelowSurface: Int,
  val terrainDensity: Double, // terrain-only density at this voxel (already blended)
  val edge: BiomeEdgeContext
)

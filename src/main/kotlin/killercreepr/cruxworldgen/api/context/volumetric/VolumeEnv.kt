package killercreepr.cruxworldgen.api.context.volumetric

data class VolumeEnv(
  val surfaceY: Int,
  val depthBelowSurface: Int,     // surfaceY - y
  val heightAboveSurface: Int,    // y - surfaceY
  val terrainDensity: Double,     // macro + detail BEFORE cave carve/add
  val seaLevel: Int
)
package killercreepr.cruxworldgen.api.context.volumetric

data class VolumeEnv(
  var surfaceY: Int = 0,
  var depthBelowSurface: Int = 0,     // surfaceY - y
  var heightAboveSurface: Int = 0,    // y - surfaceY
  var terrainDensity: Double = 0.0,     // macro + detail BEFORE cave carve/add
  var seaLevel: Int = 0
)
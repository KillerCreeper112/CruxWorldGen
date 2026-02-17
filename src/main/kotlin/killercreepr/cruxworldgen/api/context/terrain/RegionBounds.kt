package killercreepr.cruxworldgen.api.context.terrain

data class RegionBounds(
  val minX: Int, val maxX: Int,
  val minY: Int, val maxY: Int,
  val minZ: Int, val maxZ: Int,
) {
  fun contains(wx: Int, y: Int, wz: Int) : Boolean =
    wx in minX..maxX && wz in minZ..maxZ && y in minY..maxY
  fun contains(wx: Int, wz: Int) : Boolean  =
    wx in minX..maxX && wz in minZ..maxZ

  fun containsX(x : Int) = x in minX..maxX
  fun containsY(y : Int) = y in minY..maxY
  fun containsZ(z : Int) = z in minZ..maxZ
}
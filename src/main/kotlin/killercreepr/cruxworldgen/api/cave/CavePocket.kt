package killercreepr.cruxworldgen.api.cave

import killercreepr.cruxworldgen.core.cave.SimpleCavePocket

interface CavePocket {
  companion object{
    fun cavePocket(
      floorY: Int,
      ceilingY: Int
    ) : CavePocket = SimpleCavePocket(floorY, ceilingY)
  }
  val floorY: Int
  val ceilingY: Int
  val gap: Int get() = ceilingY - floorY - 1
}
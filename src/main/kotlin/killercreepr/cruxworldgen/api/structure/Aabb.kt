package killercreepr.cruxworldgen.api.structure

import killercreepr.cruxworldgen.core.structure.SimpleAabb

interface Aabb{
  companion object{
    fun aabb(
      minX: Int,
      minY: Int,
      minZ: Int,
      maxX: Int,
      maxY: Int,
      maxZ: Int
    ) : Aabb = SimpleAabb(minX, minY, minZ, maxX, maxY, maxZ)
  }

  val minX: Int
  val minY: Int
  val minZ: Int
  val maxX: Int
  val maxY: Int
  val maxZ: Int

  val sizeX: Int get() = (maxX - minX + 1)
  val sizeY: Int get() = (maxY - minY + 1)
  val sizeZ: Int get() = (maxZ - minZ + 1)
}
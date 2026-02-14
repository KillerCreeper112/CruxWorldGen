package killercreepr.cruxworldgen.api.structure

interface Aabb{
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
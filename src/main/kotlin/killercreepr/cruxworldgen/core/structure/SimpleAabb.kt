package killercreepr.cruxworldgen.core.structure

import killercreepr.cruxworldgen.api.structure.Aabb

data class SimpleAabb(
  override val minX: Int, override val minY: Int, override val minZ: Int,
  override val maxX: Int, override val maxY: Int, override val maxZ: Int
) : Aabb {
}
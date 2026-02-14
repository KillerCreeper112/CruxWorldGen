package killercreepr.cruxworldgen.core.decor

import killercreepr.cruxworldgen.api.decor.PropPoint

class SimplePropPoint(
  override val worldX: Int,
  override val worldZ: Int,
  override val localX: Int,
  override val localZ: Int,
  override val seed: Long
) : PropPoint {
}
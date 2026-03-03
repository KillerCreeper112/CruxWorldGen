package killercreepr.cruxworldgen.core.decor

import killercreepr.cruxworldgen.api.decor.VolumetricPropPoint

class SimpleVolumetricPropPoint(
  override val worldX: Int,
  override val worldY: Int,
  override val worldZ: Int,
  override val localX: Int,
  override val localZ: Int,
  override val seed: Long
) : VolumetricPropPoint {
}
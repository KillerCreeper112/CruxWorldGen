package killercreepr.cruxworldgen.core.structure

import killercreepr.cruxworldgen.api.structure.StructureInstance

class SimpleStructureInstance(
  override val worldX: Int,
  override val worldY: Int,
  override val worldZ: Int,
  override val rot: Int,
  override val seed: Long
) : StructureInstance {
}
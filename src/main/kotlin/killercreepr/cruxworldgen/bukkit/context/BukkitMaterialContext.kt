package killercreepr.cruxworldgen.bukkit.context

import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.MaterialContext

class BukkitMaterialContext(
  override val generateContext: GenerateContext,
  override val worldX: Int,
  override val y: Int,
  override val worldZ: Int,
  override val isSolid: Boolean,
  override val surfaceY: Int,
  override val depthBelowSurface: Int,
  override val airBlocksAbove: Int,
  override val caveAirBlocksBelow: Int,
  override val isUnderwater: Boolean,
  override val isSeaFloor: Boolean,
) : MaterialContext {
}
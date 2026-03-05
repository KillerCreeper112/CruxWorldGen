package killercreepr.cruxworldgen.bukkit.context

import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.MaterialContext
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.signal.SignalView

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
  override val depthFromSeaFloor: Int,
  override val signalView: SignalView,
  override val caveAirBlocksAbove: Int,
  override val solidWithoutCaves: Boolean,
  override val surfaceDepth: Int,
  override val airRun: Int
) : MaterialContext {
  override fun densityAt(
    x: Int,
    y: Int,
    z: Int
  ): DensityStack {
    return DensityStack.densityStack(0.0,0.0,0.0)
  }
}
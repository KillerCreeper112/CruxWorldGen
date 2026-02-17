package killercreepr.cruxworldgen.api.context

import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.signal.SignalView

interface MaterialContext{
  val generateContext: GenerateContext
  val worldX: Int
  val y: Int
  val worldZ: Int

  val isSolid: Boolean
  val surfaceY: Int
  val depthBelowSurface: Int     // 0 at surface, increases downward
  val airBlocksAbove: Int        // how open the sky is
  val caveAirBlocksBelow: Int    // how much empty space is under ground
  val isUnderwater: Boolean
  val depthFromSeaFloor : Int
  val signalView : SignalView

  fun densityAt(x: Int, y: Int, z: Int): DensityStack
}
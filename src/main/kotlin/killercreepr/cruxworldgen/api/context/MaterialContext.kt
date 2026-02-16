package killercreepr.cruxworldgen.api.context

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
  val isSeaFloor : Boolean
}
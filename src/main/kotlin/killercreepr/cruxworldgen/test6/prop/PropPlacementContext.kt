package killercreepr.cruxworldgen.test6.prop

data class PropPlacementContext(
  val candidate: BlockCandidate,
  val caveAirBlocksBelow: Int,
  val distanceToCaveCeiling: Int
)

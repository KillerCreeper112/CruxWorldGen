package killercreepr.cruxworldgen.api.prop

interface PropRule {
  val gridSpacingBlocks: Int
  val patternQuery: PatternQuery
  //val placementChecks: List<PlacementCheck>,
  //val placer: PropPlacer
}

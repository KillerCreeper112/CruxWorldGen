package killercreepr.cruxworldgen.core.underground

data class PlacedFeature<Cfg>(
  val feature: Feature<Cfg>,
  val cfg: Cfg,
  val modifiers: List<PlacementModifier>,
)

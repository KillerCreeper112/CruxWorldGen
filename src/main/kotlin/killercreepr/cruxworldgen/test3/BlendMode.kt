package killercreepr.cruxworldgen.test3

enum class BlendMode {
  BLEND,       // weighted average
  DOMINANT,    // only apply using core dominance
  MAX,         // max of contributions (often for carving)
  MIN
}

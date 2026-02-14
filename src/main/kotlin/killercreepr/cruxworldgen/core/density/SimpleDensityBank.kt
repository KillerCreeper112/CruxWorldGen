package killercreepr.cruxworldgen.core.density

import killercreepr.cruxworldgen.api.density.DensityBank

open class SimpleDensityBank(
  override var base: Double = 0.0,
  override var carve: Double = 0.0,
  override var add: Double = 0.0
) : DensityBank
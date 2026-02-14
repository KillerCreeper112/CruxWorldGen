package killercreepr.cruxworldgen.core.density

import killercreepr.cruxworldgen.api.density.DensityStack

open class SimpleDensityStack(
  override val base: Double,
  override val add: Double,
  override val carve: Double
) : DensityStack
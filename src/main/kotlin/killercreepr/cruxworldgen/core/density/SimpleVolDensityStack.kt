package killercreepr.cruxworldgen.core.density

import killercreepr.cruxworldgen.api.density.DensityBank
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.density.VolDensityStack

open class SimpleVolDensityStack(
  override val base: Double,
  override val add: Double,
  override val carve: Double,
  override val replaceMask: Double
) : VolDensityStack {
  override fun toBank(): DensityBank = DensityBank.densityBank(base, carve, add)
}
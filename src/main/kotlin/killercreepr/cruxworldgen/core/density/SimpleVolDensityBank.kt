package killercreepr.cruxworldgen.core.density

import killercreepr.cruxworldgen.api.density.VolDensityBank
import killercreepr.cruxworldgen.api.density.VolDensityStack

open class SimpleVolDensityBank(
  override var base: Double = 0.0,
  override var carve: Double = 0.0,
  override var add: Double = 0.0,
  override var replaceMask: Double = 0.0
): SimpleDensityBank(base, carve, add), VolDensityBank {
  override fun toStack(): VolDensityStack {
    return VolDensityStack.volDensityStack(
      base, carve, add, replaceMask
    )
  }
}
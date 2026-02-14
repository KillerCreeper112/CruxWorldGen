package killercreepr.cruxworldgen.api.density

import killercreepr.cruxworldgen.core.density.SimpleDensityStack

interface DensityStack{
  companion object{
    fun densityStack(
      base: Double = 0.0,
      carve: Double = 0.0,
      add: Double = 0.0
    ) : DensityStack = SimpleDensityStack(base, add, carve)
  }

  val base: Double
  val add: Double
  val carve: Double
  fun finalDensity(): Double = base + add - carve
}

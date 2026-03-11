package killercreepr.cruxworldgen.api.density

import killercreepr.cruxworldgen.core.density.SimpleDensityStack

interface DensityStack{
  companion object{
    fun densityStack(
      base: Double = 0.0,
      carve: Double = 0.0,
      add: Double = 0.0
    ) : DensityStack{
      if(base == 0.0 && carve == 0.0 && add == 0.0) return empty
      return SimpleDensityStack(base, add, carve)
    }
    fun emptyStack(): DensityStack = empty

    private val empty = SimpleDensityStack(0.0, 0.0, 0.0)
  }

  val base: Double
  val add: Double
  val carve: Double
  fun finalDensity(): Double = base + add - carve
  fun toBank() : DensityBank
}

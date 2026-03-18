package killercreepr.cruxworldgen.api.density

import killercreepr.cruxworldgen.core.density.SimpleVolDensityStack

interface VolDensityStack: DensityStack {
  companion object{
    fun volDensityStack(
      base: Double = 0.0,
      carve: Double = 0.0,
      add: Double = 0.0,
      replaceMask: Double = 0.0
    ): VolDensityStack{
      if(base == 0.0 && carve == 0.0 && add == 0.0 && replaceMask == 0.0) return empty
      return SimpleVolDensityStack(base, add, carve, replaceMask)
    }
    fun emptyStack(): VolDensityStack = empty

    private val empty = SimpleVolDensityStack(0.0, 0.0, 0.0, 0.0)
  }
  val replaceMask: Double
}
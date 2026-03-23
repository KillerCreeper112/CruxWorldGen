package killercreepr.cruxworldgen.api.density

import killercreepr.cruxworldgen.core.density.SimpleVolDensityBank

interface VolDensityBank: DensityBank{
  companion object{
    fun volDensityBank(
      base: Double = 0.0,
      carve: Double = 0.0,
      add: Double = 0.0,
      replaceMask: Double = 0.0
    ) : VolDensityBank = SimpleVolDensityBank(base, carve, add, replaceMask)
  }

  var replaceMask: Double

  fun addReplaceMask(mask: Double){ replaceMask += mask}

  override fun toStack() : VolDensityStack
}
package killercreepr.cruxworldgen.api.density

import killercreepr.cruxworldgen.core.density.SimpleDensityBank

interface DensityBank{
  companion object{
    fun densityBank(
      base: Double = 0.0,
      carve: Double = 0.0,
      add: Double = 0.0
    ) : DensityBank = SimpleDensityBank(base, carve, add)
  }

  fun add(bank : DensityBank){
    addBase(bank.base)
    addCarve(bank.carve)
    addAdditive(bank.add)
  }

  fun add(bank : DensityStack){
    addBase(bank.base)
    addCarve(bank.carve)
    addAdditive(bank.add)
  }

  var base: Double         // main terrain density
  var carve: Double      // subtract to carve (caves/overhangs)
  var add: Double            // add to add solids (pillars)

  fun addBase(v: Double) { base += v }
  fun addCarve(v: Double) { carve += v }
  fun addAdditive(v: Double) { add += v }

  fun finalDensity(): Double = base + add - carve

  fun toStack() : DensityStack
}
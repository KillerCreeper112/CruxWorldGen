package killercreepr.cruxworldgen.test3

class DensityStack {
  var base: Double = 0.0           // main terrain density
  var carve: Double = 0.0          // subtract to carve (caves/overhangs)
  var add: Double = 0.0            // add to add solids (pillars)

  fun addBase(v: Double) { base += v }
  fun addCarve(v: Double) { carve += v }
  fun addAdditive(v: Double) { add += v }

  fun finalDensity(): Double = base + add - carve
}

data class DensityCtx(
  val seed: Long,
  val noise: NoiseBank,
  val mix: BiomeMix
)

package killercreepr.cruxworldgen.test6.density

data class DensityStack(
  val base: Double,
  val add: Double,
  val carve: Double
) {
  fun finalDensity(): Double = base + add - carve

}

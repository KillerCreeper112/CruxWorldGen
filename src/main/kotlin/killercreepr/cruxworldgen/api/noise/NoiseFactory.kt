package killercreepr.cruxworldgen.api.noise

fun interface NoiseFactory {
  fun build(seed : Long) : NoiseField
}
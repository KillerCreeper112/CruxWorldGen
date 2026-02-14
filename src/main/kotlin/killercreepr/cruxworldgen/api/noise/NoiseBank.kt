package killercreepr.cruxworldgen.api.noise

interface NoiseBank {
  val seed : Long
  fun get(key: NoiseKey): NoiseField
  fun register(key: NoiseKey, build : NoiseFactory)
}
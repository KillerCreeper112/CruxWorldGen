package killercreepr.cruxworldgen.core.noise

import killercreepr.cruxworldgen.api.noise.NoiseBank
import killercreepr.cruxworldgen.api.noise.NoiseFactory
import killercreepr.cruxworldgen.api.noise.NoiseField
import killercreepr.cruxworldgen.api.noise.NoiseKey
import killercreepr.cruxworldgen.api.util.HashUtil

class SimpleNoiseBank(override val seed: Long) : NoiseBank {
  val cache = mutableMapOf<NoiseKey, NoiseField>()
  val factories = mutableMapOf<NoiseKey, NoiseFactory>()

  override fun get(key: NoiseKey): NoiseField = cache.getOrPut(key){
    val seed = HashUtil.saltSeed(seed, key.id)
    factories[key]?.build(seed) ?: error("No noise registered for ${key.id}")
  }

  override fun register(
    key: NoiseKey,
    build : NoiseFactory
  ) {
    if(factories.containsKey(key)) throw IllegalArgumentException("NoiseFactory already exists! $key")
    factories[key] = build
  }
}
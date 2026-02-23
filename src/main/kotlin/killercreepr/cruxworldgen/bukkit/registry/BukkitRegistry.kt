package killercreepr.cruxworldgen.bukkit.registry

import killercreepr.crux.api.registry.KeyedRegistry
import killercreepr.cruxworldgen.api.biome.Biome

object BukkitRegistry {
  val BIOME = KeyedRegistry.keyedRegistry<Biome.Keyed>()
}
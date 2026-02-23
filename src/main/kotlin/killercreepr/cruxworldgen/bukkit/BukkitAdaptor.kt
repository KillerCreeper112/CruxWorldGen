package killercreepr.cruxworldgen.bukkit

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.bukkit.registry.BukkitRegistry

object BukkitAdaptor {
  fun fromBukkit(biome : org.bukkit.block.Biome) : Biome? = BukkitRegistry.BIOME[biome.key()]
}
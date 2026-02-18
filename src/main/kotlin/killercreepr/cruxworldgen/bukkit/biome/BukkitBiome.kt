package killercreepr.cruxworldgen.bukkit.biome

import killercreepr.cruxworldgen.api.biome.Biome

interface BukkitBiome : Biome {
  fun toBukkitBiome() : org.bukkit.block.Biome
}
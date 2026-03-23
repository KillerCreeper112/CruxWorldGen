package killercreepr.cruxworldgen.bukkit.biome

import killercreepr.cruxworldgen.api.biome.Biome
import net.kyori.adventure.key.Key

interface BukkitBiome : Biome.Keyed {
  fun toBukkitBiome() : org.bukkit.block.Biome
  override fun key(): Key = toBukkitBiome().key()
}
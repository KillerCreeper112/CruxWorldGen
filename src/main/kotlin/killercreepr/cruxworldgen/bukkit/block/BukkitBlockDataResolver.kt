package killercreepr.cruxworldgen.bukkit.block

import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.block.BlockDataResolver
import killercreepr.cruxworldgen.core.block.MultiBlockResolver
import net.kyori.adventure.key.Keyed

interface BukkitBlockDataResolver : BlockDataResolver {
  fun resolve(keyed : Keyed) : BlockData = resolve(keyed.key().asString())
}

class MultiBukkitBlockDataResolver(fallbackResolver: String) : MultiBlockResolver(fallbackResolver), BukkitBlockDataResolver
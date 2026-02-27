package killercreepr.cruxworldgen.bukkit.block

import killercreepr.cruxworldgen.api.block.MultiBlockDataResolver

object BukkitBlockAdapter {
  private val blockResolver = MultiBukkitBlockDataResolver("minecraft")
  fun resolver() : BukkitBlockDataResolver = blockResolver
  fun multiResolver() : MultiBlockDataResolver = blockResolver
}
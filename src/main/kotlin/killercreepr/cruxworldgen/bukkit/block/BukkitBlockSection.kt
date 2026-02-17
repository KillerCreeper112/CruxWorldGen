package killercreepr.cruxworldgen.bukkit.block

import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.block.BlockSection

class BukkitBlockSection(
  val data : BlockData
) : BlockSection {
  override fun blockData() = data
}
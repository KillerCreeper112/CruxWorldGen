package killercreepr.cruxworldgen.bukkit.block

import killercreepr.cruxworldgen.api.block.BlockSection

class BukkitBlockSection(
  val data : BukkitDataBlockData
) : BlockSection {
  override fun blockData() = data
}
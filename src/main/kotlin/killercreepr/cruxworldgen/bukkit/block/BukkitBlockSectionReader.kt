package killercreepr.cruxworldgen.bukkit.block

import killercreepr.cruxworldgen.api.block.BlockSection
import killercreepr.cruxworldgen.api.block.BlockSectionReader
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.bukkit.region.BukkitLimitedRegion

class BukkitBlockSectionReader : BlockSectionReader {
  companion object{
    val INSTANCE = BukkitBlockSectionReader()
  }

  override fun readBlock(
    region: LimitedRegion,
    x: Int,
    y: Int,
    z: Int
  ): BlockSection = BukkitBlockSection(BukkitDataBlockData((region as BukkitLimitedRegion).region.getBlockData(x,y,z)))

  override fun canReadBlock(
    region: LimitedRegion,
    x: Int,
    y: Int,
    z: Int
  ): Boolean = region is BukkitLimitedRegion
}
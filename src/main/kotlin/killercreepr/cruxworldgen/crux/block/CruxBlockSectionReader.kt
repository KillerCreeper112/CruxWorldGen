package killercreepr.cruxworldgen.crux.block

import killercreepr.cruxcore.CruxCore
import killercreepr.cruxworldgen.api.block.BlockSection
import killercreepr.cruxworldgen.api.block.BlockSectionReader
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.bukkit.region.BukkitLimitedRegion

class CruxBlockSectionReader: BlockSectionReader {
  companion object{
    val INSTANCE = CruxBlockSectionReader()
  }

  override fun readBlock(
    region: LimitedRegion,
    x: Int,
    y: Int,
    z: Int
  ): BlockSection {
    region as BukkitLimitedRegion
    val crux = CruxCore.core().cruxBlocks().blockRegistry.getByBlockData(region.region.getBlockData(x,y,z))!!
    return CruxBlockSection(CruxBlockData(crux))
  }

  override fun canReadBlock(
    region: LimitedRegion,
    x: Int,
    y: Int,
    z: Int
  ): Boolean {
    if (region !is BukkitLimitedRegion) return false
    val crux = CruxCore.core().cruxBlocks().blockRegistry.getByBlockData(region.region.getBlockData(x,y,z))
    return crux != null
  }
}
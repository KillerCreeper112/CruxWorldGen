package killercreepr.cruxworldgen.crux.block

import killercreepr.cruxblocks.api.block.CruxBlock
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockData
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.LimitedRegion

class CruxBlockData(val block : CruxBlock) : BukkitBlockData {
  override fun setAt(ctx: ChunkGenerator.ChunkData, x: Int, y: Int, z: Int) = block.setBlock(ctx, x,y,z)
  override fun setAt(ctx: LimitedRegion, x: Int, y: Int, z: Int) = block.setBlock(ctx, x,y,z)
}
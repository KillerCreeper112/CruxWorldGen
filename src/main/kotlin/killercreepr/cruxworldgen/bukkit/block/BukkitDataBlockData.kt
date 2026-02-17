package killercreepr.cruxworldgen.bukkit.block

import org.bukkit.block.data.BlockData
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.LimitedRegion

class BukkitDataBlockData(
  val data : BlockData
) : BukkitBlockData {
  override fun setAt(
    ctx: ChunkGenerator.ChunkData,
    x: Int,
    y: Int,
    z: Int
  ) {
    ctx.setBlock(x, y, z, data)
  }

  override fun setAt(
    ctx: LimitedRegion,
    x: Int,
    y: Int,
    z: Int
  ) {
    ctx.setBlockData(x, y, z, data)
  }
}
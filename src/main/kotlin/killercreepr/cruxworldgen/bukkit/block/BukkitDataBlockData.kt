package killercreepr.cruxworldgen.bukkit.block

import killercreepr.cruxworldgen.bukkit.context.BukkitChunkContext
import org.bukkit.Material
import org.bukkit.block.BlockType
import org.bukkit.block.data.BlockData

class BukkitDataBlockData(
  val data : BlockData
) : BukkitBlockData {
  override fun setAt(
    ctx: BukkitChunkContext,
    x: Int,
    y: Int,
    z: Int
  ) {
    ctx.chunkData.setBlock(x, y, z, data)
  }
}
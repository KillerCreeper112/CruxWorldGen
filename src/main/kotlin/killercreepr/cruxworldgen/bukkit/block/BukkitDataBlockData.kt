package killercreepr.cruxworldgen.bukkit.block

import org.bukkit.Material
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

  override fun isLiquid(): Boolean {
    return when(data.material){
      Material.LAVA, Material.WATER -> true
      else -> false
    }
  }

  override fun isSolid(): Boolean {
    return data.material.isSolid
  }

  override fun isEmpty(): Boolean {
    return data.material.isEmpty
  }
}
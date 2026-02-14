package killercreepr.cruxworldgen.bukkit.context

import killercreepr.cruxworldgen.api.block.BlockState
import killercreepr.cruxworldgen.api.context.ChunkContext
import org.bukkit.Material
import org.bukkit.generator.ChunkGenerator

class BukkitChunkContext(
  override val minHeight: Int,
  override val maxHeight: Int,
  override val seaLevel: Int,
  val chunkData : ChunkGenerator.ChunkData, override val width: Int, override val depth: Int
) : ChunkContext {
  override fun setBlock(
    x: Int,
    y: Int,
    z: Int,
    state: BlockState
  ) {
    TODO("Not yet implemented")
  }

  override fun getBlock(x: Int, y: Int, z: Int): BlockState {
    TODO("Not yet implemented")
  }

  override fun isAir(x: Int, y: Int, z: Int): Boolean = chunkData.getType(x, y, z).isAir

  override fun isSolid(x: Int, y: Int, z: Int): Boolean = chunkData.getType(x, y, z).isSolid

  override fun isLiquid(x: Int, y: Int, z: Int): Boolean = when(chunkData.getType(x, y, z)) {
    Material.LAVA, Material.WATER -> true
    else -> false
  }
}
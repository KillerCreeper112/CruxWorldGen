package killercreepr.cruxworldgen.bukkit.block

import io.papermc.paper.util.ItemComponentSanitizer.override
import org.bukkit.Material
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.LimitedRegion

class BukkitMaterialBlockData(
  val type : Material
) : BukkitBlockData {
  override fun setAt(
    ctx: ChunkGenerator.ChunkData,
    x: Int,
    y: Int,
    z: Int
  ) {
    ctx.setBlock(x, y, z, type)
  }

  override fun setAt(
    ctx: LimitedRegion,
    x: Int,
    y: Int,
    z: Int
  ) {
    ctx.setType(x, y, z, type)
  }

  override fun isLiquid(): Boolean = when(type){
    Material.WATER, Material.LAVA -> true
    else -> false
  }

  override fun isSolid(): Boolean = type.isSolid

  override fun isEmpty(): Boolean = type.isEmpty
}
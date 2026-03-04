package killercreepr.cruxworldgen.crux.block

import killercreepr.cruxblocks.api.block.group.CruxBlockGroup
import killercreepr.cruxblocks.core.block.texture.MaterialTextureData
import killercreepr.cruxblocks.core.block.texture.NoteTextureData
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockData
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.LimitedRegion

class CruxBlockGroupData(val block : CruxBlockGroup) : BukkitBlockData {
  override fun setAt(ctx: ChunkGenerator.ChunkData, x: Int, y: Int, z: Int) = block.setBlock(ctx, x,y,z)
  override fun setAt(ctx: LimitedRegion, x: Int, y: Int, z: Int) = block.setBlock(ctx, x,y,z)

  override fun isLiquid(): Boolean = false

  override fun isSolid(): Boolean = when(block.baseBlock.textureData){
    is NoteTextureData -> true
    is MaterialTextureData -> (block.baseBlock.textureData as MaterialTextureData).material.isSolid
    else -> false
  }

  override fun isEmpty(): Boolean = false
}
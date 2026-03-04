package killercreepr.cruxworldgen.crux.block

import killercreepr.cruxblocks.api.block.CruxBlock
import killercreepr.cruxblocks.core.block.texture.MaterialTextureData
import killercreepr.cruxblocks.core.block.texture.NoteTextureData
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockData
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.LimitedRegion

class CruxBlockData(val block : CruxBlock) : BukkitBlockData {
  override fun setAt(ctx: ChunkGenerator.ChunkData, x: Int, y: Int, z: Int) = block.setBlock(ctx, x,y,z)
  override fun setAt(ctx: LimitedRegion, x: Int, y: Int, z: Int) = block.setBlock(ctx, x,y,z)
  override fun isLiquid(): Boolean = false//todo crux liquid support when?

  override fun isSolid(): Boolean = when(block.textureData){
    is NoteTextureData -> true
    is MaterialTextureData -> (block.textureData as MaterialTextureData).material.isSolid
    else -> false
  }

  override fun isEmpty(): Boolean = false //custom blocks should never be empty
}
package killercreepr.cruxworldgen.bukkit.block

import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.bukkit.context.BukkitChunkContext

interface BukkitBlockData : BlockData {
  fun setAt(ctx : BukkitChunkContext, x : Int, y : Int, z : Int)
}
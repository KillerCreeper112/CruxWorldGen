package killercreepr.cruxworldgen.bukkit.block

import killercreepr.cruxworldgen.api.block.BlockData
import org.bukkit.generator.ChunkGenerator

interface BukkitBlockData : BlockData {
  fun setAt(ctx : ChunkGenerator.ChunkData, x : Int, y : Int, z : Int)
  fun setAt(ctx : org.bukkit.generator.LimitedRegion, x : Int, y : Int, z : Int)
}
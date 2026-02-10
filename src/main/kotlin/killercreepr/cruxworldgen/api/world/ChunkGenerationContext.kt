package killercreepr.cruxworldgen.api.world

import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.WorldInfo
import java.util.*

interface ChunkGenerationContext{
  val world: WorldInfo
  val chunkData: ChunkGenerator.ChunkData
  val chunkX: Int
  val chunkZ: Int
  val random: Random
}

class SimpleChunkGenerationContext(
  override val world: WorldInfo,
  override val chunkData: ChunkGenerator.ChunkData,
  override val chunkX: Int,
  override val chunkZ: Int,
  override val random: Random
) : ChunkGenerationContext
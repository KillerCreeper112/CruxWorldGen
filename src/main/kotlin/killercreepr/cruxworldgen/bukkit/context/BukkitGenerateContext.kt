package killercreepr.cruxworldgen.bukkit.context

import killercreepr.cruxworldgen.api.context.ChunkContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.WorldContext
import killercreepr.cruxworldgen.api.noise.NoiseBank
import java.util.*

class BukkitGenerateContext(
  override val worldContext: WorldContext,
  override val random: Random,
  override val chunkX: Int,
  override val chunkZ: Int,
  override val chunkContext: ChunkContext,
  override val noise: NoiseBank
) : GenerateContext {
  override fun wrapLocalX(worldX: Int) : Int = Math.floorMod(worldX, chunkContext.width)

  override fun wrapLocalZ(worldZ: Int) : Int = Math.floorMod(worldZ, chunkContext.depth)

  override fun toLocalXIfInChunk(worldX: Int): Int? {
    val base = chunkX * chunkContext.width
    val lx = worldX - base
    return if (lx in 0 until chunkContext.width) lx else null
  }

  override fun toLocalZIfInChunk(worldZ: Int): Int? {
    val base = chunkZ * chunkContext.depth
    val lz = worldZ - base
    return if (lz in 0 until chunkContext.depth) lz else null
  }

  override fun toWorldX(localX: Int) : Int = chunkX * chunkContext.width + localX

  override fun toWorldZ(localZ: Int) : Int = chunkZ * chunkContext.depth + localZ
}
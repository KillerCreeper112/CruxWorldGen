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
  override val queries = BukkitTerrainQueries(this)
}
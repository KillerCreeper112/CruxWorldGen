package killercreepr.cruxworldgen.core

import killercreepr.cruxworldgen.api.world.ChunkGenerationContext
import killercreepr.cruxworldgen.test.ClimateAbyssTerrainGenerator
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.WorldInfo
import java.util.*

class BukkitGenAdapter(val gen : ClimateAbyssTerrainGenerator) : ChunkGenerator() {
  override fun generateSurface(
    worldInfo: WorldInfo,
    random: Random,
    chunkX: Int,
    chunkZ: Int,
    chunkData: ChunkData
  ) {
    gen.generate(
      object : ChunkGenerationContext {
        override val world: WorldInfo = worldInfo
        override val chunkData: ChunkData = chunkData
        override val chunkX: Int = chunkX
        override val chunkZ: Int = chunkZ
        override val random: Random = random
      }
    )
  }
}
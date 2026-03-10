package killercreepr.cruxworldgen.api.generation.chunk

import killercreepr.cruxworldgen.api.generation.GenerationPipeline
import killercreepr.cruxworldgen.api.noise.NoiseBank
import killercreepr.cruxworldgen.api.signal.SignalWriter
import killercreepr.cruxworldgen.bukkit.generation.WorldDetails
import org.bukkit.generator.WorldInfo
import java.util.*

interface ChunkSampler{
  val generation: GenerationPipeline
  val noise: NoiseBank
  val worldDetails: WorldDetails
  val biomeCellSize: Int
  val mediumCellSize: Int
    get() = 2
  fun sample(worldInfo: WorldInfo, random: Random, chunkX: Int, chunkZ: Int,
             signalWriter: SignalWriter): SampledChunk
}
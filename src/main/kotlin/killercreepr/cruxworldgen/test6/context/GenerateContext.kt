package killercreepr.cruxworldgen.test6.context

import killercreepr.cruxworldgen.test6.noise.NoiseBank
import java.util.Random

class GenerateContext(
  val worldContext : WorldContext,
  val random : Random,
  val chunkX : Int,
  val chunkZ : Int,
  val chunkContext : ChunkContext,
  val noise : NoiseBank
) {
}
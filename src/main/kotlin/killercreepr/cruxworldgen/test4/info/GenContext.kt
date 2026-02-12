package killercreepr.cruxworldgen.test4.info

import killercreepr.cruxworldgen.test3.NoiseBank
import java.util.Random

class GenContext(
  val worldContext : WorldContext,
  val random: Random,
  val chunkX: Int,
  val chunkZ: Int,
  val chunkContext: ChunkContext,
  val noise : NoiseBank
) {
}
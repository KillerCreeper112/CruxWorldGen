package killercreepr.cruxworldgen.test6.context

import killercreepr.cruxworldgen.test6.noise.NoiseBank
import killercreepr.cruxworldgen.test6.prop.TerrainQueries
import java.util.Random

class GenerateContext(
  val worldContext : WorldContext,
  val random : Random,
  val chunkX : Int,
  val chunkZ : Int,
  val chunkContext : ChunkContext,
  val noise : NoiseBank
) {
  val queries = TerrainQueries(this)
  fun normalizedY(y: Int): Double {
    val minY = chunkContext.minHeight
    val maxYExclusive = chunkContext.maxHeight
    val heightRange = (maxYExclusive - minY).coerceAtLeast(1)
    return ((y - minY).toDouble() / heightRange.toDouble()).coerceIn(0.0, 1.0)
  }

  fun band(center01: Double, halfWidth01: Double, y01: Double): Double {
    // 1 at center, fades to 0 outside roughly +/- halfWidth
    val t = kotlin.math.abs(y01 - center01) / halfWidth01
    val clamped = t.coerceIn(0.0, 1.0)
    // smoothstep down: 1 -> 0
    val s = clamped * clamped * (3.0 - 2.0 * clamped)
    return 1.0 - s
  }

}
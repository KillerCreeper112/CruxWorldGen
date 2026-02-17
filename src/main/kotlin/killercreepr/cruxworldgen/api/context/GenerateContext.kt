package killercreepr.cruxworldgen.api.context

import killercreepr.cruxworldgen.api.noise.NoiseBank
import killercreepr.cruxworldgen.api.util.Curve
import java.util.*

interface GenerateContext{
  val worldContext : WorldContext
  val random : Random
  val chunkX : Int
  val chunkZ : Int
  val chunkContext : ChunkContext
  val noise : NoiseBank

  fun normalizedY(y: Int): Double {
    val minY = chunkContext.minHeight
    val maxYExclusive = chunkContext.maxHeight
    val heightRange = (maxYExclusive - minY).coerceAtLeast(1)
    return ((y - minY).toDouble() / heightRange.toDouble()).coerceIn(0.0, 1.0)
  }

  fun band(center01: Double, halfWidth01: Double, y01: Double): Double = Curve.band(center01, halfWidth01, y01)

  fun wrapLocalX(worldX : Int) : Int
  fun wrapLocalZ(worldZ : Int) : Int

  fun toWorldX(localX : Int) : Int
  fun toWorldZ(localZ : Int) : Int

  fun toLocalXIfInChunk(worldX: Int): Int?
  fun toLocalZIfInChunk(worldZ: Int): Int?
}
package killercreepr.cruxworldgen.api.util

class SeededRng(seed: Long) {
  private var state = if (seed != 0L) seed else 0x9E3779B97F4A7C1L

  private fun nextLongRaw(): Long {
    state = state * 6364136223846793005L + 1442695040888963407L
    var z = state
    z = (z xor (z ushr 30)) * -4658895280553007687L
    z = (z xor (z ushr 27)) * -7723592293110705685L
    return z xor (z ushr 31)
  }

  fun nextDouble(): Double {
    return ((nextLongRaw() ushr 11).toDouble()) / (1L shl 53).toDouble()
  }

  fun nextDouble(min: Double, max: Double): Double {
    return min + nextDouble() * (max - min)
  }

  fun nextInt(min: Int, max: Int): Int {
    if (max <= min) return min
    return min + (nextDouble() * (max - min + 1)).toInt().coerceAtMost(max - min)
  }
}
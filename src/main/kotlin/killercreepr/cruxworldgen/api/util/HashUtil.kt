package killercreepr.cruxworldgen.api.util

object HashUtil {
  const val HASH_SALT: Long = -7046029254386353131L //0x9E3779B97F4A7C15L
  const val HASH_MUL_X: Long = 7145483588892929177L
  const val HASH_MIX_1: Long = -4658895280553007687L
  const val HASH_MIX_2: Long = -7723592293110705685L

  /** Mixes bits well (SplitMix64 mix). */
  fun mix64(x0: Long): Long {
    var x = x0
    x = (x xor (x ushr 30)) * -4658895280553007687L
    x = (x xor (x ushr 27)) * -7723592293110705685L
    return x xor (x ushr 31)
  }

  /** Deterministic random in [0, 1). */
  fun hash01(seed: Long): Double {
    val v = mix64(seed)
    val positive = v ushr 1 // keep it non-negative (0-Long.MAX_VALUE)
    return positive.toDouble() / (Long.MAX_VALUE.toDouble() + 1.0)
  }

  fun hashSigned01(seed: Long): Double {
    var v = seed
    v = (v xor (v ushr 30)) * -4658895280553007687L
    v = (v xor (v ushr 27)) * -7723592293110705685L
    v = v xor (v ushr 31)
    // map to [-1,1]
    val u = (v and Long.MAX_VALUE).toDouble() / Long.MAX_VALUE.toDouble()
    return u * 2.0 - 1.0
  }

  /** Deterministic random integer in [min-max]. */
  fun chooseInt(seed: Long, min: Int, max: Int): Int {
    if (max <= min) return min
    val r = hash01(seed)
    return (min + (r * (max - min + 1)).toInt()).coerceIn(min, max)
  }

  fun hash2D(seed: Long, x: Int, z: Int): Long {
    var value = seed
    value = value xor (x.toLong() * HASH_MUL_X)
    value = value xor (z.toLong() * HASH_SALT)
    value = (value xor (value ushr 30)) * HASH_MIX_1
    value = (value xor (value ushr 27)) * HASH_MIX_2
    return value xor (value ushr 31)
  }

  fun saltSeed(seed: Long, id: String): Long {
    // simple deterministic mix; good enough for noise seeding
    var h = 1125899906842597L
    for (c in id) h = 31L * h + c.code
    return seed xor h
  }
}
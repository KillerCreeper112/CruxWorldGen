package killercreepr.cruxworldgen.api.util

object HashUtil {
  const val HASH_SALT: Long = -7046029254386353131L //0x9E3779B97F4A7C15L
  const val HASH_MUL_X: Long = 7145483588892929177L
  const val HASH_MUL_Y: Long = 8329402038393918132L
  const val HASH_MIX_1: Long = -4658895280553007687L
  const val HASH_MIX_2: Long = -7723592293110705685L

  val PHI: Long = 0x9E3779B97F4A7C15uL.toLong()

  fun chance(seed: Long, pTrue: Double): Boolean {
    val clamped = pTrue.coerceIn(0.0, 1.0)
    val r = chooseInt(seed, 0, 9999)
    return r < (clamped * 10000.0).toInt()
  }

  fun mixSeed(
    seed: Long,
    x: Int, y: Int, z: Int,
    salt: Long
  ): Long {
    var h = seed xor salt

    // Accumulate all fields (multiplication overflow is intended)
    h = h * PHI + x.toLong()
    h = h * PHI + y.toLong()
    h = h * PHI + z.toLong()

    // SplitMix64 finalizer (avalanche)
    h = h xor (h ushr 30)
    h *= 0xBF58476D1CE4E5B9uL.toLong()
    h = h xor (h ushr 27)
    h *= 0x94D049BB133111EBuL.toLong()
    h = h xor (h ushr 31)

    return h
  }

  fun mixSeed(
    seed: Long,
    x: Int, y: Int, z: Int,
    dx: Int, dy: Int, dz: Int,
    salt: Long
  ): Long {
    var h = seed xor salt

    // Accumulate all fields (multiplication overflow is intended)
    h = h * PHI + x.toLong()
    h = h * PHI + y.toLong()
    h = h * PHI + z.toLong()
    h = h * PHI + dx.toLong()
    h = h * PHI + dy.toLong()
    h = h * PHI + dz.toLong()

    // SplitMix64 finalizer (avalanche)
    h = h xor (h ushr 30)
    h *= 0xBF58476D1CE4E5B9uL.toLong()
    h = h xor (h ushr 27)
    h *= 0x94D049BB133111EBuL.toLong()
    h = h xor (h ushr 31)

    return h
  }

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

  fun hash3D(seed: Long, x: Int, y: Int, z: Int): Long {
    var value = seed
    value = value xor (x.toLong() * HASH_MUL_X)
    value = value xor (y.toLong() * HASH_MUL_Y)
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
package killercreepr.cruxworldgen.api.signal

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap

interface SignalKey<T> {
  companion object{
    /*open class IntKey : SignalKey<Int> {
      override fun max(oldValue: Int, newValue: Int): Int = oldValue.coerceAtLeast(newValue)
    }
    open class FloatKey : SignalKey<Float> {
      override fun max(oldValue: Float, newValue: Float): Float = oldValue.coerceAtLeast(newValue)
    }*/
    fun doubleSignalKey() : SignalKey<Double> = DoubleSignalKey()

    open class DoubleSignalKey : SignalKey<Double> {
      override fun max(oldValue: Double, newValue: Double): Double = oldValue.coerceAtLeast(newValue)
      override fun min(oldValue: Double, newValue: Double): Double = oldValue.coerceAtMost(newValue)
      override fun buildSignalLayer(): SignalLayer<Double> = DoubleSignalLayer()
    }
    open class DoubleSignalLayer : SignalLayer<Double> {
      val map = Long2DoubleOpenHashMap(64)

      override fun get(idx: Long): Double = map[idx]

      override fun set(idx: Long, value: Double) {
        map[idx] = value
      }

      override fun getOrDefault(idx: Long, fallback: () -> Double): Double = map.getOrElse(idx, fallback)

      override fun getOrNullable(idx: Long, fallback: () -> Double?): Double? = map.getOrElse(idx, fallback)

      override fun getOrDefault(idx: Long, fallback: Double): Double = map.getOrDefault(idx, fallback)

      override fun getOrNullable(idx: Long, fallback: Double?): Double? = map.getOrDefault(idx, fallback)

      override fun getIfPresent(idx: Long): Double? = map.get(idx)
      override fun isPresent(idx: Long): Boolean = map.containsKey(idx)

      override fun max(idx: Long, v: Double) {
        val cur = map[idx]
        if (v > cur) map[idx] = v
      }
    }

    open class GenericSignalLayer<T>(val map : MutableMap<Long, T>) : SignalLayer<T> {

      override fun get(idx: Long): T = map[idx]!!

      override fun set(idx: Long, value: T) {
        map[idx] = value
      }

      override fun getOrDefault(idx: Long, fallback: () -> T): T = map.getOrElse(idx, fallback)

      override fun getOrNullable(idx: Long, fallback: () -> T?): T? = map.getOrElse(idx, fallback)

      override fun getOrDefault(idx: Long, fallback: T): T = map.getOrDefault(idx, fallback)

      override fun getOrNullable(idx: Long, fallback: T?): T? = map.getOrDefault(idx, fallback)

      override fun getIfPresent(idx: Long): T? = map[idx]
      override fun isPresent(idx: Long): Boolean = map.containsKey(idx)

      override fun max(idx: Long, v: T) = set(idx, v)
    }

  }
  fun max(oldValue : T, newValue : T) : T
  fun min(oldValue : T, newValue : T) : T

  fun buildSignalLayer(): SignalLayer<T>
}
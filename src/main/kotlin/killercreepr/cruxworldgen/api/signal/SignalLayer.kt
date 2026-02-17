package killercreepr.cruxworldgen.api.signal

interface SignalLayer<T> {
  fun get(idx: Long): T?
  fun set(idx: Long, value: T)
  fun max(idx: Long, v: T)

  fun getOrDefault(idx: Long, fallback: () -> T): T
  fun getOrNullable(idx: Long, fallback: () -> T?): T?
  fun getOrDefault(idx: Long, fallback : T): T
  fun getOrNullable(idx: Long, fallback : T?): T?

  fun getIfPresent(idx: Long) : T?
  fun isPresent(idx: Long): Boolean
}
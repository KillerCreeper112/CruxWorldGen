package killercreepr.cruxworldgen.api.signal

interface SignalView {
  fun <T> getOrDefault(x : Int, y : Int, z : Int, key: SignalKey<T>, fallback: () -> T): T
  fun <T> getOrNullable(x : Int, y : Int, z : Int, key: SignalKey<T>, fallback: () -> T?): T?
  fun <T> getOrDefault(x : Int, y : Int, z : Int, key: SignalKey<T>, fallback : T): T
  fun <T> getOrNullable(x : Int, y : Int, z : Int, key: SignalKey<T>, fallback : T?): T?
  fun <T> get(x : Int, y : Int, z : Int, key : SignalKey<T>) : T
  fun <T> getIfPresent(x : Int, y : Int, z : Int, key : SignalKey<T>) : T?
  fun isPresent(x : Int, y : Int, z : Int, key : SignalKey<Any>) : Boolean
}
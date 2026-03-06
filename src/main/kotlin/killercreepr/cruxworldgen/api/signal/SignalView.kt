package killercreepr.cruxworldgen.api.signal

interface SignalView {
  fun <T> getOrDefault(x : Int, y : Int, z : Int, key: SignalKey<T>, fallback: () -> T): T
  fun <T> getOrNullable(x : Int, y : Int, z : Int, key: SignalKey<T>, fallback: () -> T?): T?
  fun <T> getOrDefault(x : Int, y : Int, z : Int, key: SignalKey<T>, fallback : T): T
  fun <T> getOrNullable(x : Int, y : Int, z : Int, key: SignalKey<T>, fallback : T?): T?
  fun <T> get(x : Int, y : Int, z : Int, key : SignalKey<T>) : T
  fun <T> getIfPresent(x : Int, y : Int, z : Int, key : SignalKey<T>) : T?
  fun isPresent(x : Int, y : Int, z : Int, key : SignalKey<Any>) : Boolean


  fun <T> columnGetOrDefault(
    x : Int, z : Int,
    key: SignalKey<T>,
    fallback: () -> T
  ): T

  fun <T> columnGetOrNullable(x : Int, z : Int, key: SignalKey<T>, fallback: () -> T?): T?

  fun <T> columnGetOrDefault(x : Int,  z : Int, key: SignalKey<T>, fallback: T): T

  fun <T> columnGetOrNullable(x : Int,  z : Int, key: SignalKey<T>, fallback: T?): T?

  fun <T> columnGet(x : Int,  z : Int, key: SignalKey<T>): T

  fun <T> columnGetIfPresent(x : Int, z : Int, key: SignalKey<T>): T?

  fun columnIsPresent(x : Int, z : Int, key: SignalKey<Any>): Boolean
}
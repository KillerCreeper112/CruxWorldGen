package killercreepr.cruxworldgen.api.signal

interface SignalWriter {
  fun <T> max(x : Int, y : Int, z : Int, key : SignalKey<T>, value : T)
  fun <T> min(x : Int, y : Int, z : Int, key : SignalKey<T>, value : T)
  fun <T> set(x : Int, y : Int, z : Int, key : SignalKey<T>, value : T)
}
package killercreepr.cruxworldgen.api.signal

import killercreepr.cruxworldgen.core.signal.DummySignalWriter

interface SignalWriter {
  fun <T> max(x : Int, y : Int, z : Int, key : SignalKey<T>, value : T)
  fun <T> set(x : Int, y : Int, z : Int, key : SignalKey<T>, value : T)
}
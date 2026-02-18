package killercreepr.cruxworldgen.api.signal

import killercreepr.cruxworldgen.core.signal.DummySignalWriter

interface SignalHandler : SignalView, SignalWriter{
  companion object{
    val DUMMY : SignalHandler = DummySignalWriter()
  }
}
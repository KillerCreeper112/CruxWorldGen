package killercreepr.cruxworldgen.core.signal

import killercreepr.cruxworldgen.api.signal.SignalHandler
import killercreepr.cruxworldgen.api.signal.SignalKey
import killercreepr.cruxworldgen.api.signal.SignalLayer

class SimpleSignalWriter(
  override val data : MutableMap<SignalKey<*>, SignalLayer<*>>
) : SimpleSignalView(data), SignalHandler {
  override fun <T> max(x : Int, y : Int, z : Int, key: SignalKey<T>, value: T) {
    val existing = getIfPresent(x,y,z, key)
    if(existing == null){
      set(x,y,z,key, value)
      return
    }
    set(x,y,z,key, key.max(existing, value))
  }

  override fun <T> set(x : Int, y : Int, z : Int, key: SignalKey<T>, value: T){
    val existing = data[key] as? SignalLayer<T>
    if(existing == null){
      val layer = key.buildSignalLayer()
      data[key] = layer
      layer.set(voxelKey(x,y,z), value)
      return
    }
    existing.set(voxelKey(x,y,z), value)
  }

}
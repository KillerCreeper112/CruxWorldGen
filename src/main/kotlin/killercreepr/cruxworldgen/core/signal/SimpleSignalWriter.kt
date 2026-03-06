package killercreepr.cruxworldgen.core.signal

import killercreepr.cruxworldgen.api.signal.SignalHandler
import killercreepr.cruxworldgen.api.signal.SignalKey
import killercreepr.cruxworldgen.api.signal.SignalLayer

class SimpleSignalWriter(
  override val data : MutableMap<SignalKey<*>, SignalLayer<*>>,
  override val columData : MutableMap<SignalKey<*>, SignalLayer<*>>
) : SimpleSignalView(data, columData), SignalHandler {
  override fun <T> max(x : Int, y : Int, z : Int, key: SignalKey<T>, value: T) {
    val existing = getIfPresent(x,y,z, key)
    if(existing == null){
      set(x,y,z,key, value)
      return
    }
    set(x,y,z,key, key.max(existing, value))
  }

  override fun <T> min(
    x: Int,
    y: Int,
    z: Int,
    key: SignalKey<T>,
    value: T
  ) {
    val existing = getIfPresent(x,y,z, key)
    if(existing == null){
      set(x,y,z,key, value)
      return
    }
    set(x,y,z,key, key.min(existing, value))
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

  override fun <T> columnMax(
    x: Int,
    z: Int,
    key: SignalKey<T>,
    value: T
  ) {
    val existing = columnGetIfPresent(x,z, key)
    if(existing == null){
      columnSet(x,z,key, value)
      return
    }
    columnSet(x,z,key, key.max(existing, value))
  }

  override fun <T> columnMin(
    x: Int,
    z: Int,
    key: SignalKey<T>,
    value: T
  ) {
    val existing = columnGetIfPresent(x,z, key)
    if(existing == null){
      columnSet(x,z,key, value)
      return
    }
    columnSet(x,z,key, key.min(existing, value))
  }

  override fun <T> columnSet(
    x: Int,
    z: Int,
    key: SignalKey<T>,
    value: T
  ) {
    val existing = columData[key] as? SignalLayer<T>
    if(existing == null){
      val layer = key.buildSignalLayer()
      columData[key] = layer
      layer.set(columnKey(x,z), value)
      return
    }
    existing.set(columnKey(x,z), value)
  }

}
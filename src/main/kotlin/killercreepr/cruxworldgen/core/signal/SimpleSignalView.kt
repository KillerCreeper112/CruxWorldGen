package killercreepr.cruxworldgen.core.signal

import killercreepr.cruxworldgen.api.signal.SignalKey
import killercreepr.cruxworldgen.api.signal.SignalLayer
import killercreepr.cruxworldgen.api.signal.SignalView

open class SimpleSignalView(
  open val data : Map<SignalKey<*>, SignalLayer<*>>
): SignalView {
  companion object{
    fun voxelKey(x: Int, y: Int, z: Int): Long {
      // Example pack (works if your ranges fit; adjust bit widths to your world)
      val lx = (x.toLong() and 0x3FFFFFF)      // 26 bits
      val lz = (z.toLong() and 0x3FFFFFF)      // 26 bits
      val ly = (y.toLong() and 0xFFF)          // 12 bits (-2048..2047 if biased separately)
      return (lx shl 38) or (lz shl 12) or ly
    }
  }

  override fun <T> getOrDefault(
    x : Int, y : Int, z : Int,
    key: SignalKey<T>,
    fallback: () -> T
  ): T{
    val layer = data[key] as? SignalLayer<T> ?: return fallback()
    val key = voxelKey(x,y,z)
    return layer.getOrDefault(key, fallback)
  }

  override fun <T> getOrNullable(x : Int, y : Int, z : Int, key: SignalKey<T>, fallback: () -> T?): T?{
    val layer = data[key] as? SignalLayer<T> ?: return fallback()
    val key = voxelKey(x,y,z)
    return layer.getOrNullable(key, fallback)
  }

  override fun <T> getOrDefault(x : Int, y : Int, z : Int, key: SignalKey<T>, fallback: T): T{
    val layer = data[key] as? SignalLayer<T> ?: return fallback
    val key = voxelKey(x,y,z)
    return layer.getOrDefault(key, fallback)
  }

  override fun <T> getOrNullable(x : Int, y : Int, z : Int, key: SignalKey<T>, fallback: T?): T?{
    val layer = data[key] as? SignalLayer<T> ?: return fallback
    val key = voxelKey(x,y,z)
    return layer.getOrNullable(key, fallback)
  }

  override fun <T> get(x : Int, y : Int, z : Int, key: SignalKey<T>): T{
    val layer = data[key] as SignalLayer<T>
    val key = voxelKey(x,y,z)
    return layer.get(key)!!
  }

  override fun <T> getIfPresent(x : Int, y : Int, z : Int, key: SignalKey<T>): T?{
    val layer = data[key] as? SignalLayer<T> ?: return null
    val key = voxelKey(x,y,z)
    return layer.getIfPresent(key)
  }

  override fun isPresent(x : Int, y : Int, z : Int, key: SignalKey<Any>): Boolean{
    val layer = data[key] ?: return false
    val key = voxelKey(x,y,z)
    return layer.isPresent(key)
  }
}
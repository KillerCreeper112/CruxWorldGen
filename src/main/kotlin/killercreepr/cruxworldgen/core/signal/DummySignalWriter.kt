package killercreepr.cruxworldgen.core.signal

import killercreepr.cruxworldgen.api.signal.SignalHandler
import killercreepr.cruxworldgen.api.signal.SignalKey

class DummySignalWriter : SignalHandler {
  override fun <T> max(
    x: Int,
    y: Int,
    z: Int,
    key: SignalKey<T>,
    value: T
  ) {
  }

  override fun <T> min(
    x: Int,
    y: Int,
    z: Int,
    key: SignalKey<T>,
    value: T
  ) {
  }

  override fun <T> set(
    x: Int,
    y: Int,
    z: Int,
    key: SignalKey<T>,
    value: T
  ) {

  }

  override fun <T> columnMax(
    x: Int,
    z: Int,
    key: SignalKey<T>,
    value: T
  ) {
  }

  override fun <T> columnMin(
    x: Int,
    z: Int,
    key: SignalKey<T>,
    value: T
  ) {
  }

  override fun <T> columnSet(
    x: Int,
    z: Int,
    key: SignalKey<T>,
    value: T
  ) {
  }

  override fun <T> getOrDefault(
    x: Int,
    y: Int,
    z: Int,
    key: SignalKey<T>,
    fallback: () -> T
  ): T = fallback()

  override fun <T> getOrNullable(
    x: Int,
    y: Int,
    z: Int,
    key: SignalKey<T>,
    fallback: () -> T?
  ): T? = fallback()

  override fun <T> getOrDefault(
    x: Int,
    y: Int,
    z: Int,
    key: SignalKey<T>,
    fallback: T
  ): T = fallback

  override fun <T> getOrNullable(
    x: Int,
    y: Int,
    z: Int,
    key: SignalKey<T>,
    fallback: T?
  ): T? = fallback

  override fun <T> get(
    x: Int,
    y: Int,
    z: Int,
    key: SignalKey<T>
  ): T = error("Trying to get from DummySignalWriter $key ($x,$y,$z)")

  override fun <T> getIfPresent(
    x: Int,
    y: Int,
    z: Int,
    key: SignalKey<T>
  ): T? = null

  override fun isPresent(
    x: Int,
    y: Int,
    z: Int,
    key: SignalKey<Any>
  ): Boolean = false

  override fun <T> columnGetOrDefault(
    x: Int,
    z: Int,
    key: SignalKey<T>,
    fallback: () -> T
  ): T = fallback.invoke()

  override fun <T> columnGetOrNullable(
    x: Int,
    z: Int,
    key: SignalKey<T>,
    fallback: () -> T?
  ): T? = fallback.invoke()

  override fun <T> columnGetOrDefault(
    x: Int,
    z: Int,
    key: SignalKey<T>,
    fallback: T
  ): T = fallback

  override fun <T> columnGetOrNullable(
    x: Int,
    z: Int,
    key: SignalKey<T>,
    fallback: T?
  ): T? = fallback

  override fun <T> columnGet(x: Int, z: Int, key: SignalKey<T>): T = error("Trying to get column from DummySignalWriter $key ($x,$z)")

  override fun <T> columnGetIfPresent(
    x: Int,
    z: Int,
    key: SignalKey<T>
  ): T? = null

  override fun columnIsPresent(
    x: Int,
    z: Int,
    key: SignalKey<Any>
  ): Boolean = false
}
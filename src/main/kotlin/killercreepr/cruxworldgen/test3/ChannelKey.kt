package killercreepr.cruxworldgen.test3

typealias ChannelKey = String

class ChannelStack {
  private val blend = HashMap<ChannelKey, Double>()
  private val dominant = HashMap<ChannelKey, Double>()
  private val max = HashMap<ChannelKey, Double>()
  private val min = HashMap<ChannelKey, Double>()

  fun add(key: ChannelKey, value: Double, mode: BlendMode) {
    when (mode) {
      BlendMode.BLEND -> blend[key] = (blend[key] ?: 0.0) + value
      BlendMode.DOMINANT -> dominant[key] = (dominant[key] ?: 0.0) + value
      BlendMode.MAX -> max[key] = kotlin.math.max(max[key] ?: Double.NEGATIVE_INFINITY, value)
      BlendMode.MIN -> min[key] = kotlin.math.min(min[key] ?: Double.POSITIVE_INFINITY, value)
    }
  }

  fun getBlend(key: ChannelKey, default: Double = 0.0) = blend[key] ?: default
  fun getDominant(key: ChannelKey, default: Double = 0.0) = dominant[key] ?: default
  fun getMax(key: ChannelKey, default: Double = 0.0) = max[key] ?: default
  fun getMin(key: ChannelKey, default: Double = 0.0) = min[key] ?: default
}

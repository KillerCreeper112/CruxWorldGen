package killercreepr.cruxworldgen.extension

import killercreepr.cruxworldgen.api.util.NoiseUtil

fun Number.remap01(): Double = NoiseUtil.remap01(this.toDouble())
package killercreepr.cruxworldgen.test3

fun interface Noise2D {
  fun sample(x: Double, z: Double): Double // [-1,1]
}

fun interface Noise3D {
  fun sample(x: Double, y: Double, z: Double): Double // [-1,1]
}

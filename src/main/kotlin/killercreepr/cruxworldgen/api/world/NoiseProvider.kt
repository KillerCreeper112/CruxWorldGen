package killercreepr.cruxworldgen.api.world

interface NoiseProvider {
    fun noise2D(x: Double, z: Double): Double
    fun noise3D(x: Double, y: Double, z: Double): Double
    fun noise3D(x: Int, y: Int, z: Int): Double = noise3D(x.toDouble(), y.toDouble(), z.toDouble())
}

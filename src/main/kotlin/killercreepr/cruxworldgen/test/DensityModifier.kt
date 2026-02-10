package killercreepr.cruxworldgen.test

fun interface DensityModifier {
    fun modify(x: Int, y: Int, z: Int, current: Double): Double
}

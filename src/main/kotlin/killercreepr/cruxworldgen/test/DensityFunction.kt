package killercreepr.cruxworldgen.test

fun interface DensityFunction {
    fun compute(x: Int, y: Int, z: Int, biome: AbyssBiome): Double
}

package killercreepr.cruxworldgen.test

interface StructureInfluence {
    fun influence(x: Int, y: Int, z: Int, currentDensity: Double): Double
}

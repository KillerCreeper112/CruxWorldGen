package killercreepr.cruxworldgen.test

interface BiomeResolver {
    fun getBiome(x: Int, y: Int, z: Int): AbyssBiome
}

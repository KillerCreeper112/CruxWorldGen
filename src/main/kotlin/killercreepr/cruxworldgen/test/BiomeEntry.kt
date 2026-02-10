package killercreepr.cruxworldgen.test

import killercreepr.cruxworldgen.api.world.NoiseProvider

data class BiomeEntry(
    val biome: AbyssBiome,
    val weightFunc: (x: Int, y: Int, z: Int, noise: NoiseProvider) -> Double
)

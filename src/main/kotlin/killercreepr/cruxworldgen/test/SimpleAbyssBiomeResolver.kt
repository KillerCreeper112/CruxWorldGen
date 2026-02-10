package killercreepr.cruxworldgen.test

import killercreepr.cruxworldgen.api.world.NoiseProvider

class SimpleAbyssBiomeResolver(
    private val noise: NoiseProvider,
    private val plague: AbyssBiome,
    private val charred: AbyssBiome
) : BiomeResolver {

    override fun getBiome(x: Int, y: Int, z: Int): AbyssBiome {

        val biomeNoise = noise.noise2D(x * 0.0008, z * 0.0008)

        return if (biomeNoise > 0.0) plague else charred
    }
}

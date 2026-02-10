package killercreepr.cruxworldgen.test

import killercreepr.cruxworldgen.api.world.NoiseProvider

class ExpandableAbyssBiomeResolver(
    private val entries: List<BiomeEntry>,
    private val noise: NoiseProvider
) : BiomeResolver {

    /*override fun getBiome(x: Int, y: Int, z: Int): AbyssBiome {

        // Compute weights
        val weights = entries.map { it.biome to it.weightFunc(x, y, z, noise) }

        // Pick biome with highest weight
        return weights.maxByOrNull { it.second }?.first ?: entries.first().biome
    }*/

    fun getAllWeights(x: Int, y: Int, z: Int): List<Pair<AbyssBiome, Double>> {
        val raw = entries.map { it.biome to it.weightFunc(x, y, z, noise) }
        val total = raw.sumOf { it.second }.coerceAtLeast(1e-6)
        return raw.map { it.first to it.second / total } // normalized
    }

    override fun getBiome(x: Int, y: Int, z: Int): AbyssBiome {
        // fallback: max weight
        return getAllWeights(x, y, z).maxByOrNull { it.second }!!.first
    }

    /*override fun getBiome(x: Int, y: Int, z: Int): AbyssBiome {

        // Compute raw weights
        val rawWeights = entries.map { it.biome to it.weightFunc(x, y, z, noise) }

        // Normalize weights to sum = 1
        val total = rawWeights.sumOf { it.second }
        if (total <= 0.0) return entries.first().biome // fallback

        val normalizedWeights = rawWeights.map { it.first to it.second / total }

        *//*val blendedDensity = normalizedWeights.sumOf { (biome, weight) ->
            biome.modifyDensity(baseDensity, x, y, z, noise) * weight
        }*//*


        // Pick the biome with the highest normalized weight
        // This can be swapped later for true blending if you want
        return normalizedWeights.maxByOrNull { it.second }!!.first
    }*/

}

package killercreepr.cruxworldgen.test2

import killercreepr.cruxworldgen.test2.biome.BadlandsPlateauBiome
import killercreepr.cruxworldgen.test2.biome.CharredWastesBiome
import killercreepr.cruxworldgen.test2.biome.PlagueAbyssBiome
import kotlin.math.abs

class BiomeRegistry(private val seed: Long) {

    private val biomes = listOf<VolumetricBiome>(
      PlagueAbyssBiome(),
      CharredWastesBiome(),
      BadlandsPlateauBiome()
        // Add more biomes here
    )

    fun fromCell(cellX: Int, cellY: Int, cellZ: Int): VolumetricBiome {
        val hash = hash(cellX, cellY, cellZ)
        return biomes[(hash % biomes.size).toInt()]
    }

    fun getBiomeByHash(sectionX: Int, sectionZ: Int, worldSeed: Long = 0L): VolumetricBiome {
        var hash = sectionX * 374761393 + sectionZ * 668265263 + worldSeed.toInt()
        hash = (hash xor (hash shr 13)) * 1274126177
        val biomeIndex = abs(hash) % biomes.size
        return biomes[biomeIndex]
    }

    fun getBiomeFromNoise(f1: Double): VolumetricBiome {
        val hash = ((f1 * 10_000).toLong() xor seed)
        val index = kotlin.math.abs(hash % biomes.size).toInt()
        return biomes[index]
    }

    fun hash(x: Int, y: Int, z: Int): Long {
        var h = seed
        h = h * 31 + x
        h = h * 31 + y
        h = h * 31 + z
        return kotlin.math.abs(h)
    }
}

package killercreepr.cruxworldgen.test

import killercreepr.cruxworldgen.api.world.*
import kotlin.math.pow
import kotlin.math.sqrt

class ClimateAbyssTerrainGenerator(
  val noise: NoiseProvider,
  private val climateNoise: ClimateNoiseProvider, // 3D climate noise
  private val biomes: List<AbyssBiome>,
  private val cubeSize: Int = 16 // size of the biome cube
) : StageGenerator {

    override val stage = GenerationStage.TERRAIN

    override fun generate(context: ChunkGenerationContext) {
        val chunkX = context.chunkX
        val chunkZ = context.chunkZ

        for (cubeX in 0 until 16 step cubeSize) {
            for (cubeZ in 0 until 16 step cubeSize) {
                for (cubeY in context.world.minHeight..context.world.maxHeight step cubeSize) {

                    val worldX = chunkX * 16 + cubeX
                    val worldZ = chunkZ * 16 + cubeZ
                    val worldY = cubeY

                    // --- 1️⃣ Sample climate once per cube ---
                    val climateVector = sampleClimate(worldX, worldY, worldZ)

                    // --- 2️⃣ Pick dominant biome for this cube ---
                    val cubeBiome = selectDominantBiome(climateVector)

                    // --- 3️⃣ Fill cube with terrain ---
                    for (x in cubeX until (cubeX + cubeSize).coerceAtMost(16)) {
                        for (z in cubeZ until (cubeZ + cubeSize).coerceAtMost(16)) {
                            for (y in cubeY until (cubeY + cubeSize).coerceAtMost(context.world.maxHeight + 1)) {
                                val worldBlockX = chunkX * 16 + x
                                val worldBlockY = y
                                val worldBlockZ = chunkZ * 16 + z

                                val density = computeDensity(worldBlockX, worldBlockY, worldBlockZ, cubeBiome)
                                if (density > 0) {
                                    val block = cubeBiome.surfaceRule.resolve(
                                      worldBlockX, worldBlockY, worldBlockZ, density, cubeBiome
                                    )
                                    context.chunkData.setBlock(x, y, z, block)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Sample 3D climate vector */
    private fun sampleClimate(x: Int, y: Int, z: Int): ClimateVector {
        return ClimateVector(
          temperature = climateNoise.temperatureNoise.noise3D(x, y, z),
          humidity = climateNoise.humidityNoise.noise3D(x, y, z),
          continental = climateNoise.continentalNoise.noise3D(x, y, z),
          erosion = climateNoise.erosionNoise.noise3D(x, y, z),
          weirdness = climateNoise.weirdNoise.noise3D(x, y, z)
        )
    }

    /** Select dominant biome based on distance in climate space */
    private fun selectDominantBiome(climate: ClimateVector): AbyssBiome {
        val k = 5.0
        return biomes.maxByOrNull { biome ->
            val dist2 = (climate.temperature - biome.temp).pow(2) +
              (climate.humidity - biome.humidity).pow(2) +
              (climate.continental - biome.continental).pow(2) +
              (climate.erosion - biome.erosion).pow(2) +
              (climate.weirdness - biome.weirdness).pow(2)
            kotlin.math.exp(-k * dist2) // smoother chance for distant biomes
        }!!
    }

    /** Compute density using base + biome modifiers */
    private fun computeDensity(x: Int, y: Int, z: Int, biome: AbyssBiome): Double {
        // Base density from climate noises
        val baseDensity = AbyssBaseDensity(climateNoise).compute(x, y, z, biome)

        // Apply biome-specific scale to control mountains vs flat
        val scaledDensity = baseDensity * biomeScale(biome)

        return scaledDensity
    }

    /** Return scale factor for biome to control height amplitude */
    private fun biomeScale(biome: AbyssBiome): Double {
        // Example: Charred Wastes flatter, Plague Mire more mountainous
        return when (biome) {
            is CharredWastesBiome -> 0.6
            is PlagueMireBiome -> 1.2
            else -> 1.0
        }
    }

    /** Simple container for climate values */
    private data class ClimateVector(
      val temperature: Double,
      val humidity: Double,
      val continental: Double,
      val erosion: Double,
      val weirdness: Double
    )
}

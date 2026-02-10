package killercreepr.cruxworldgen.test

import killercreepr.cruxworldgen.api.world.NoiseProvider

class AbyssBaseDensity(
  private val climateNoise: ClimateNoiseProvider
) : DensityFunction {

    override fun compute(x: Int, y: Int, z: Int, biome: AbyssBiome): Double {
        /*val tempNoise = climateNoise.temperatureNoise.noise3D(x.toDouble(), y.toDouble(), z.toDouble())
        val humidityNoise = climateNoise.humidityNoise.noise3D(x.toDouble(), y.toDouble(), z.toDouble())
        val continentalNoise = climateNoise.continentalNoise.noise3D(x.toDouble(), y.toDouble(), z.toDouble())
        val erosionNoise = climateNoise.erosionNoise.noise3D(x.toDouble(), y.toDouble(), z.toDouble())
        val weirdNoise = climateNoise.weirdNoise.noise3D(x.toDouble(), y.toDouble(), z.toDouble())

        val continental = continentalNoise * (biome.continental * 100) // big influence
        val erosion     = erosionNoise     * (biome.erosion * 50)      // medium
        val weirdness   = weirdNoise       * (biome.weirdness * 30)    // small
        val baseHeight = 60 + continental + erosion + weirdness

        // 3️⃣ Subtract Y to get density
        var density = baseHeight - y

        // 4️⃣ Apply biome-specific density modifiers
        biome.densityModifiers.forEach { density = it.modify(x, y, z, density) }

        return density*/
        val continental = climateNoise.continentalNoise.noise3D(x.toDouble(), 0.0, z.toDouble())
        val erosion = climateNoise.erosionNoise.noise3D(x.toDouble(), 0.0, z.toDouble())
        val weird = climateNoise.weirdNoise.noise3D(x.toDouble(), 0.0, z.toDouble())

        // Base height for all biomes
        val baseHeight = 60 + continental * 50 + erosion * 20 + weird * 10

        // Apply biome-specific delta
        var delta = 0.0
        for (modifier in biome.densityModifiers) {
            delta = modifier.modify(x, y, z, delta)
        }

        // Combine base + delta and subtract Y for density
        return baseHeight + delta - y
    }
}


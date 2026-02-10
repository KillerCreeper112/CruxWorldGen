package killercreepr.cruxworldgen.test2.biome

import killercreepr.cruxworldgen.test2.BaseBiome
import killercreepr.cruxworldgen.test2.BiomeFeature
import org.bukkit.Material
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class BadlandsPlateauBiome : BaseBiome() {
    init {
        features.add(MesaSurfaceFeature())

        heightNoise.frequency(0.01)
        terrainNoiseGen.frequency(0.05)
    }

    // Base height for large plateaus
    override fun baseHeight(x: Int, z: Int): Double {
        val noiseHeight = heightNoise.noise(x.toDouble(), z.toDouble()) * 20.0
        val plateauVariation = sin(x * 0.01) * 5 + cos(z * 0.01) * 5
        val cliffs = sin(x * 0.05) * 10 + cos(z * 0.05) * 10
        return 70.0 + noiseHeight + plateauVariation + cliffs
    }

    // Terrain noise: adds cliffs and small ridges
    override fun terrainNoise(x: Int, y: Int, z: Int): Double {
        val horizontalNoise = sin(x * 0.03) * 3 + sin(z * 0.03) * 3
        val verticalFalloff = max(0.0, (y - 65) * 0.5)
        return horizontalNoise - verticalFalloff
    }

    // Vertical bias: slightly favor higher plateaus
    override fun verticalBias(y: Int): Double {
        return if (y < 60) (y - 60) * 0.05 else 0.0
    }
}

// ------------------------ Features ------------------------

class MesaSurfaceFeature : BiomeFeature {

    override fun modifyDensity(x: Int, y: Int, z: Int, currentDensity: Double): Double {
        val plateauTop = 70.0
        val bump = sin(x * 0.02) * 2 + cos(z * 0.02) * 2
        val strata = ((y / 5) % 2) * 0.5
        return currentDensity + (plateauTop - y) * 0.1 + bump + strata
    }

    override fun getBlock(x: Int, y: Int, z: Int, currentDensity: Double): Material? {
        if (currentDensity <= 0) return null
        return when ((y / 5) % 3) {
            0 -> Material.RED_SANDSTONE
            1 -> Material.ORANGE_TERRACOTTA
            else -> Material.YELLOW_TERRACOTTA
        }
    }
}


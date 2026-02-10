package killercreepr.cruxworldgen.test2.biome

import killercreepr.cruxworldgen.test2.BaseBiome
import killercreepr.cruxworldgen.test2.biome.feature.CaveFeature
import killercreepr.cruxworldgen.test2.biome.feature.FungalSurfaceFeature
import kotlin.math.cos
import kotlin.math.sin

class PlagueAbyssBiome : BaseBiome() {

    init {
        // Modular features
        features.add(FungalSurfaceFeature())
        //features.add(CaveFeature())
        //features.add(AbyssLayerFeature())
        //features.add(BoneSpikeFeature())
        //features.add(RiverFeature())

        heightNoise.frequency(0.01)
        terrainNoiseGen.frequency(0.05)
    }

    // Base height for rolling plague hills
    override fun baseHeight(x: Int, z: Int): Double {
        val noiseHeight = heightNoise.noise(x.toDouble(), z.toDouble()) * 25.0
        val hill = sin(x * 0.01) * 8 + cos(z * 0.01) * 8
        return 65.0 + noiseHeight + hill
    }

    override fun terrainNoise(x: Int, y: Int, z: Int): Double {
        return terrainNoiseGen.noise(x.toDouble(), y.toDouble(), z.toDouble()) * 5.0
    }

    override fun verticalBias(y: Int): Double {
        return if (y < 50) (50 - y) * 0.3 else 0.0
    }
}

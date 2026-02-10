package killercreepr.cruxworldgen.test2.biome

import killercreepr.cruxworldgen.test2.BaseBiome
import killercreepr.cruxworldgen.test2.biome.feature.CaveFeature
import killercreepr.cruxworldgen.test2.biome.feature.CharredSurfaceFeature
import killercreepr.cruxworldgen.test2.biome.feature.EmberDepositFeature
import killercreepr.cruxworldgen.test2.biome.feature.HollowRockFeature
import org.bukkit.Material
import kotlin.math.cos
import kotlin.math.sin

class CharredWastesBiome : BaseBiome() {

    init {
        // Add modular features
        //features.add(CaveFeature())
        features.add(CharredSurfaceFeature())
        //features.add(EmberDepositFeature())
        //features.add(HollowRockFeature())
    }

    // Low, jagged hills with cracks
    override fun baseHeight(x: Int, z: Int): Double {
        // Low amplitude hills
        val smallHills = sin(x * 0.02) * 2 + cos(z * 0.02) * 2
        return 60.0 + smallHills
    }

    // Terrain noise: subtle
    override fun terrainNoise(x: Int, y: Int, z: Int): Double {
        // Very gentle floating chunks
        return sin(x * 0.03) * 1.5 + sin(z * 0.03) * 1.5 - y * 0.01
    }

    // Vertical bias: small effect to slightly favor lower sections
    override fun verticalBias(y: Int): Double {
        return if (y < 45) (45 - y) * 0.1 else 0.0
    }
}

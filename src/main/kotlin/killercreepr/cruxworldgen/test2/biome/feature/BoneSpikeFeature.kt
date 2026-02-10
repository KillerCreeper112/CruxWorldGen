package killercreepr.cruxworldgen.test2.biome.feature

import killercreepr.cruxworldgen.test2.BiomeFeature
import org.bukkit.Material
import kotlin.random.Random

class BoneSpikeFeature : BiomeFeature {
    private val random = Random(12345)

    override fun modifyDensity(x: Int, y: Int, z: Int, currentDensity: Double): Double {
        // Small spikes randomly appear in mid-depth layers
        if (y in 40..60 && currentDensity > 0) {
            if (random.nextDouble() < 0.005) {
                return currentDensity + 15
            }
        }
        return currentDensity
    }

    override fun getBlock(x: Int, y: Int, z: Int, currentDensity: Double): Material? {
        if (y in 40..60 && currentDensity > 0) {
            if (random.nextDouble() < 0.01) return Material.BONE_BLOCK
        }
        return null
    }
}

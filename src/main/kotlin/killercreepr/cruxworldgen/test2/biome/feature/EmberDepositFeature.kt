package killercreepr.cruxworldgen.test2.biome.feature

import killercreepr.cruxworldgen.test2.BiomeFeature
import org.bukkit.Material
import kotlin.math.cos
import kotlin.math.sin

class EmberDepositFeature : BiomeFeature {
    override fun modifyDensity(x: Int, y: Int, z: Int, currentDensity: Double): Double {
        // Hollow pockets for ember veins
        val noise = sin(x * 0.05) * cos(y * 0.05) * sin(z * 0.05)
        return if (noise > 0.6) currentDensity - 20 else currentDensity
    }

    override fun getBlock(x: Int, y: Int, z: Int, currentDensity: Double): Material? {
        val noise = sin(x * 0.05) * cos(y * 0.05) * sin(z * 0.05)
        if (noise > 0.6 && currentDensity > 0) {
            return Material.MAGMA_BLOCK
        }
        return null
    }
}

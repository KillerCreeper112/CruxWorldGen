package killercreepr.cruxworldgen.test2.biome.feature

import killercreepr.cruxworldgen.test2.BiomeFeature
import org.bukkit.Material

class CharredSurfaceFeature : BiomeFeature {
    override fun modifyDensity(x: Int, y: Int, z: Int, currentDensity: Double): Double = currentDensity

    override fun getBlock(x: Int, y: Int, z: Int, currentDensity: Double): Material? {
        return when {
            y > 55 && currentDensity > 0 -> Material.RED_CONCRETE // surface char
            y in 50..55 && currentDensity > 0 -> Material.RED_WOOL
            y in 45..50 && currentDensity > 0 -> Material.MAGMA_BLOCK
            y < 45 && currentDensity > 0 -> Material.RED_TERRACOTTA
            else -> null//Material.MAGMA_BLOCK
        }
    }
}

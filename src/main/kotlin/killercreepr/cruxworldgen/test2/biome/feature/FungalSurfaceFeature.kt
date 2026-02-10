package killercreepr.cruxworldgen.test2.biome.feature

import killercreepr.cruxworldgen.test2.BiomeFeature
import org.bukkit.Material

class FungalSurfaceFeature : BiomeFeature {
    override fun modifyDensity(x: Int, y: Int, z: Int, currentDensity: Double): Double = currentDensity

    override fun getBlock(x: Int, y: Int, z: Int, currentDensity: Double): Material? {
        return when {
            y > 60 && currentDensity > 0 -> Material.GREEN_WOOL
            y in 55..60 && currentDensity > 0 -> Material.GREEN_CONCRETE
            y < 55 && currentDensity > 0 -> Material.LIME_TERRACOTTA
            else -> null//Material.GREEN_CONCRETE
        }
    }
}

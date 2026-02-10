package killercreepr.cruxworldgen.test2.biome.feature

import killercreepr.cruxworldgen.test2.BiomeFeature
import org.bukkit.Material

class CaveFeature : BiomeFeature {
    override fun modifyDensity(x: Int, y: Int, z: Int, currentDensity: Double): Double {
        val noise = kotlin.math.sin(x * 0.05) * kotlin.math.cos(y * 0.05) * kotlin.math.sin(z * 0.05)
        return if (noise > 0.5) currentDensity - 40 else currentDensity
    }

    override fun getBlock(x: Int, y: Int, z: Int, currentDensity: Double): Material? = null
}

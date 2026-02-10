package killercreepr.cruxworldgen.test2.biome.feature

import killercreepr.cruxworldgen.test2.BiomeFeature
import org.bukkit.Material
import kotlin.math.cos
import kotlin.math.sin

class HollowRockFeature : BiomeFeature {
    override fun modifyDensity(x: Int, y: Int, z: Int, currentDensity: Double): Double {
        // Hollow overhangs / floating chunks
        val noise = sin(x * 0.02) * cos(y * 0.02) * sin(z * 0.02)
        return if (noise > 0.7) currentDensity - 30 else currentDensity
    }

    override fun getBlock(x: Int, y: Int, z: Int, currentDensity: Double): Material? = null
}

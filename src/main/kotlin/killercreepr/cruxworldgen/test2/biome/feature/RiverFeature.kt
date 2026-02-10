package killercreepr.cruxworldgen.test2.biome.feature

import killercreepr.cruxworldgen.test2.BiomeFeature
import org.bukkit.Material
import kotlin.math.cos
import kotlin.math.sin

class RiverFeature : BiomeFeature {
    // Replace with your Worley2D river mask if you want
    override fun modifyDensity(x: Int, y: Int, z: Int, currentDensity: Double): Double {
        val riverMask = (sin(x * 0.03) + cos(z * 0.03)) * 0.5
        return currentDensity - riverMask * 12
    }

    override fun getBlock(x: Int, y: Int, z: Int, currentDensity: Double): Material? {
        return null // Let density carving create air / water
    }
}

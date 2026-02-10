package killercreepr.cruxworldgen.test2.biome.feature

import killercreepr.cruxworldgen.test2.BiomeFeature
import org.bukkit.Material

class AbyssLayerFeature : BiomeFeature {
    override fun modifyDensity(x: Int, y: Int, z: Int, currentDensity: Double): Double {
        return if (y < 40) currentDensity + 10 else currentDensity
    }

    override fun getBlock(x: Int, y: Int, z: Int, currentDensity: Double): Material? {
        return if (y < 40 && currentDensity > 0) Material.OBSIDIAN else null
    }
}

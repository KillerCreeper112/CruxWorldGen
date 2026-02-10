package killercreepr.cruxworldgen.test2

import org.bukkit.Material

interface BiomeFeature {
    /**
     * Modify the density at a position.
     * Return the new density.
     */
    fun modifyDensity(x: Int, y: Int, z: Int, currentDensity: Double): Double

    /**
     * Decide which block to place at a voxel after density.
     * Return null to leave unchanged.
     */
    fun getBlock(x: Int, y: Int, z: Int, currentDensity: Double): Material?
}

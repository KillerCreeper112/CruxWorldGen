package killercreepr.cruxworldgen.test2

import org.bukkit.Material

interface VolumetricBiome {
    fun baseHeight(x: Int, z: Int): Double
    fun terrainNoise(x: Int, y: Int, z: Int): Double
    fun caveStrength(): Double
    fun verticalBias(y: Int): Double

    fun density(x: Int, y: Int, z: Int): Double

    fun getBlock(x: Int, y: Int, z: Int, currentDensity: Double): Material

    fun getBlockBlended(
      x: Int,
      y: Int,
      z: Int,
      density: Double,
      other: VolumetricBiome,
      blend: Double
    ): Material {
        // Let this biome propose a block
        val blockA = this.getBlock(x, y, z, density)
        val blockB = other.getBlock(x, y, z, density)

        // If one of them is AIR, favor the solid one
        if (blockA == Material.AIR) return blockB
        if (blockB == Material.AIR) return blockA

        // Otherwise interpolate — you can also do a weighted random or pick lighter material
        return if (blend < 0.5) blockA else blockB
    }
}

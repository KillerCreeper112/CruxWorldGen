package killercreepr.cruxworldgen.test2

import killercreepr.cruxworldgen.test2.biome.decoration.DecorationContext
import killercreepr.cruxworldgen.test2.biome.decoration.DecoratorFeature
import org.bukkit.Material

interface VolumetricBiome {
    val decorators : MutableList<DecoratorFeature>
    fun baseHeight(x: Int, z: Int): Double
    fun terrainNoise(x: Int, y: Int, z: Int): Double
    fun verticalBias(y: Int): Double

    // NEW: how much this biome likes this 3D section
    fun habitatWeight(sx: Int, sy: Int, sz: Int): Double


    fun density(x: Int, y: Int, z: Int): Double

    fun getBlock(x: Int, y: Int, z: Int, currentDensity: Double): Material

    /**
     * Backwards-compatible: runs decorators for the chunk center origin.
     * Existing code that calls decorate(chunkX, chunkZ, context) can still work,
     * but the generator will call placeFromOrigin for a radius of origins.
     */

    fun decorate(chunkX: Int, chunkZ: Int, context: DecorationContext)
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

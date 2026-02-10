/*
package killercreepr.cruxworldgen.test

import killercreepr.cruxworldgen.api.world.ChunkGenerationContext
import killercreepr.cruxworldgen.api.world.GenerationStage
import killercreepr.cruxworldgen.api.world.NoiseProvider
import killercreepr.cruxworldgen.api.world.StageGenerator
import org.bukkit.Material
import kotlin.math.max

class ModularAbyssTerrainGenerator(
  private val noise: NoiseProvider,
  private val biomeResolver: ExpandableAbyssBiomeResolver
) : StageGenerator {

    override val stage = GenerationStage.TERRAIN

    */
/** Only blend when secondary biome is strong enough at edges *//*

    private val edgeBlendThreshold = 1

    override fun generate(context: ChunkGenerationContext) {
        val chunkX = context.chunkX
        val chunkZ = context.chunkZ

        for (x in 0 until 16) {
            for (z in 0 until 16) {
                val worldX = chunkX * 16 + x
                val worldZ = chunkZ * 16 + z

                for (y in context.world.minHeight..context.world.maxHeight) {

                    // 1️⃣ Get all biome weights at this 3D position
                    val weightsAtPos = biomeResolver.getAllWeights(worldX, y, worldZ)
                        .sortedByDescending { it.second }

                    val (dominantBiome, dominantWeight) = weightsAtPos[0]
                    val (secondaryBiome, secondaryWeight) = weightsAtPos.getOrElse(1) { dominantBiome to 0.0 }

                    // 2️⃣ Only blend near edges
                    val shouldBlend = (dominantWeight - secondaryWeight) > edgeBlendThreshold

                    val density = if (shouldBlend) {
                        val total = dominantWeight + secondaryWeight
                        val dominantFactor = dominantWeight / total
                        val secondaryFactor = secondaryWeight / total

                        computeBiomeDensity(worldX, y, worldZ, dominantBiome) * dominantFactor +
                          computeBiomeDensity(worldX, y, worldZ, secondaryBiome) * secondaryFactor
                    } else {
                        computeBiomeDensity(worldX, y, worldZ, dominantBiome)
                    }

                    // 3️⃣ Place block if density > 0
                    if (density > 0) {
                        val surfaceBlock = dominantBiome.surfaceRule.resolve(
                          worldX, y, worldZ,
                          density = 1.0,
                          biome = dominantBiome
                        )
                        context.chunkData.setBlock(x, y, z, surfaceBlock)
                    }
                }
            }
        }
    }

    */
/** Compute biome-specific density using continental/detail scales, offsets, and modifiers *//*

    private fun computeBiomeDensity(x: Int, y: Int, z: Int, biome: AbyssBiome): Double {
        val continental = noise.noise2D(x * biome.continentalScale + biome.offsetX,
          z * biome.continentalScale + biome.offsetZ)
        val detail = noise.noise2D(x * biome.detailScale + biome.offsetX,
          z * biome.detailScale + biome.offsetZ)

        var density = 70 + continental * 60 + detail * 40 - y

        biome.densityModifiers.forEach { density = it.modify(x, y, z, density) }
        return density
    }
}
*/

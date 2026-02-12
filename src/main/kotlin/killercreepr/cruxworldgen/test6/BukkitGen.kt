package killercreepr.cruxworldgen.test6

import killercreepr.cruxworldgen.test6.context.ChunkContext
import killercreepr.cruxworldgen.test6.context.GenerateContext
import killercreepr.cruxworldgen.test6.context.WorldContext
import killercreepr.cruxworldgen.test6.density.DensityBank
import killercreepr.cruxworldgen.test6.material.MaterialContext
import killercreepr.cruxworldgen.test6.noise.NoiseBank
import org.bukkit.Material
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.WorldInfo
import java.util.*

class BukkitGen(
  val pipeline: GenerationPipeline
) : ChunkGenerator() {
  override fun generateNoise(
    worldInfo: WorldInfo,
    random: Random,
    chunkX: Int,
    chunkZ: Int,
    chunkData: ChunkData
  ) {
    val chunkWidth = 16
    val chunkLength = 16

    val noise = NoiseBank(worldInfo.seed)

    val ctx = GenerateContext(
      WorldContext(worldInfo.seed),
      random, chunkX, chunkZ,
      object: ChunkContext(chunkData.minHeight, chunkData.maxHeight, 64){
        override fun setBlock(x: Int, y: Int, z: Int, material: Material) {
          chunkData.setBlock(x, y, z, material)
        }
      },
      noise
    )

    val minHeight = ctx.chunkContext.minHeight
    val maxHeight = ctx.chunkContext.maxHeight
    for (localX in 0 until chunkWidth) {
      for (localZ in 0 until chunkLength) {

        val worldX = chunkX * chunkWidth + localX
        val worldZ = chunkZ * chunkLength + localZ

        val zone = pipeline.zones.sampleZone(ctx, worldX, worldZ)

        // NEW: get primary + secondary + weights
        val biomeBlend = zone.biomes.sampleBiomeBlend(ctx, worldX, worldZ)

        for (y in (maxHeight - 1) downTo minHeight) {

          val density = pipeline.blendedBiomeDensity(ctx, biomeBlend, worldX, y, worldZ)

          val detailNoiseValue = ctx.noise.detail3D(worldX, y, worldZ)
          val detailDensity = detailNoiseValue * 3.0

          val finalDensity = density.finalDensity() + detailDensity
          val isSolid = finalDensity > 0.0


          val materialContext = MaterialContext(
            worldX = worldX,
            y = y,
            worldZ = worldZ,
            isSolid = isSolid,
            surfaceY = 0,
            depthBelowSurface = 0,
            airBlocksAbove = 0,
            caveAirBlocksBelow = 0,
            isUnderwater = false
          )

          /*val primaryWeightedBiome = biomeBlend.weightedBiomes.maxBy { it.weight }
          val secondaryWeightedBiome = biomeBlend.weightedBiomes.minBy { it.weight }

          val primaryBiome = primaryWeightedBiome.biome
          val primaryWeight = primaryWeightedBiome.weight
          val secondaryBiome = secondaryWeightedBiome.biome

// Deterministic per-column random value in [0,1)
          val columnHash = (worldX * 73428767) xor (worldZ * 912931) xor (ctx.worldContext.seed.toInt())
          val columnRandom01 = ((columnHash * 1103515245 + 12345) ushr 1).toDouble() / Int.MAX_VALUE.toDouble()

          val materialBiome =
            if (columnRandom01 < primaryWeight) primaryBiome else secondaryBiome

          val chosenMaterial = materialBiome.materialProvider.chooseMaterial(materialContext)*/

          // For now, choose material from primary biome only
          val chosenMaterial = biomeBlend.primaryBiome().materialProvider.chooseMaterial(materialContext)

          if (chosenMaterial != Material.AIR) {
            ctx.chunkContext.setBlock(localX, y, localZ, chosenMaterial)
          }
        }
      }
    }
  }
}
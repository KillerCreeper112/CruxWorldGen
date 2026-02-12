package killercreepr.cruxworldgen.test6

import killercreepr.crux.api.block.CruxBlockWrapper.material
import killercreepr.cruxworldgen.test6.biome.BiomeBlendSample
import killercreepr.cruxworldgen.test6.context.ChunkContext
import killercreepr.cruxworldgen.test6.context.GenerateContext
import killercreepr.cruxworldgen.test6.context.WorldContext
import killercreepr.cruxworldgen.test6.decor.DecorationPass
import killercreepr.cruxworldgen.test6.density.DensityBank
import killercreepr.cruxworldgen.test6.material.MaterialContext
import killercreepr.cruxworldgen.test6.noise.NoiseBank
import killercreepr.cruxworldgen.test6.prop.CavernPillarRule
import killercreepr.cruxworldgen.test6.prop.PropPointGrid
import killercreepr.cruxworldgen.test6.prop.TerrainQueries
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.WorldInfo
import java.util.*

class BukkitGen(
  val pipeline: GenerationPipeline,
  val decor : DecorationPipeline
) : ChunkGenerator() {
  fun findSurfaceY(ctx: GenerateContext, biomeBlend: BiomeBlendSample, worldX: Int, worldZ: Int): Int {
    val minY = ctx.chunkContext.minHeight
    val maxY = ctx.chunkContext.maxHeight - 1

    for (y in maxY downTo minY) {
      val terrainDensity = pipeline.terrainDensityNoCaves(ctx, biomeBlend, worldX, y, worldZ)
      if (terrainDensity > 0.0) return y
    }
    return minY
  }


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

        override fun getBlock(x: Int, y: Int, z: Int): Material = chunkData.getType(x, y, z)
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

        val biomeBlend = zone.biomes.sampleBiomeBlend(ctx, worldX, worldZ)
        val surfaceY = findSurfaceY(ctx, biomeBlend, worldX, worldZ)

        for (y in (maxHeight - 1) downTo minHeight) {
          val terrainDensity = pipeline.terrainDensityNoCaves(ctx, biomeBlend, worldX, y, worldZ)
          val caveCarve = pipeline.blendedBiomeCarve(ctx, biomeBlend, worldX, y, worldZ, surfaceY, terrainDensity)

          val finalDensity = terrainDensity - caveCarve
          val isSolid = finalDensity > 0.0
          val depthBelowSurface = surfaceY - y

          val materialContext = MaterialContext(
            worldX = worldX,
            y = y,
            worldZ = worldZ,
            isSolid = isSolid,
            surfaceY = surfaceY,
            depthBelowSurface = depthBelowSurface,
            airBlocksAbove = 0,
            caveAirBlocksBelow = 0,
            isUnderwater = false
          )

          val chosenMaterial = biomeBlend.primaryBiome().materialProvider.chooseMaterial(materialContext)

          if (chosenMaterial != Material.AIR) {
            ctx.chunkContext.setBlock(localX, y, localZ, chosenMaterial)
          }
        }
      }
    }

    decor.runAllPasses(ctx, chunkX, chunkZ) { wx, wz ->
      val zone = pipeline.zones.sampleZone(ctx, wx, wz)
      zone.biomes.sampleBiomeBlend(ctx, wx, wz)
    }
  }
}
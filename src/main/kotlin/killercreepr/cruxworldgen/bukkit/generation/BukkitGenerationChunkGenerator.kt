package killercreepr.cruxworldgen.bukkit.generation

import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.decor.DecorationPipeline
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.generation.GenerationPipeline
import killercreepr.cruxworldgen.api.noise.NoiseBank
import killercreepr.cruxworldgen.api.structure.StructurePipeline
import killercreepr.cruxworldgen.bukkit.context.BukkitChunkContext
import killercreepr.cruxworldgen.bukkit.context.BukkitGenerateContext
import killercreepr.cruxworldgen.bukkit.context.BukkitMaterialContext
import killercreepr.cruxworldgen.bukkit.context.BukkitWorldContext
import killercreepr.cruxworldgen.core.noise.BaseNoiseKeys
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.WorldInfo
import java.util.*

data class WorldDetails(
  val seaLevel : Int,
  val chunkWidth : Int,
  val chunkDepth : Int
)

class BukkitGenerationChunkGenerator(
  val generation : GenerationPipeline,
  val decorations : DecorationPipeline,
  val structures : StructurePipeline,
  val noise : NoiseBank,
  val worldDetails : WorldDetails
) : ChunkGenerator() {
  fun findSurfaceY(ctx: GenerateContext, biomeBlend: BiomeBlendSample, worldX: Int, worldZ: Int): Int {
    val minY = ctx.chunkContext.minHeight
    val maxY = ctx.chunkContext.maxHeight - 1

    for (y in maxY downTo minY) {
      val terrainDensity = generation.terrainDensityNoCaves(ctx, biomeBlend, worldX, y, worldZ)
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
    val chunkWidth = worldDetails.chunkWidth
    val chunkDepth = worldDetails.chunkDepth

    val ctx = BukkitGenerateContext(
      BukkitWorldContext(worldInfo),
      random, chunkX, chunkZ,
      BukkitChunkContext(chunkData.minHeight, chunkData.maxHeight, worldDetails.seaLevel, chunkData, chunkWidth, chunkDepth),
      noise
    )

    //val cacheMap = mutableMapOf<Pair<Int, Int>, BiomeBlendSample>()
    val minHeight = ctx.chunkContext.minHeight
    val maxHeight = ctx.chunkContext.maxHeight
    for (localX in 0 until chunkWidth) {
      for (localZ in 0 until chunkDepth) {

        val worldX = chunkX * chunkWidth + localX
        val worldZ = chunkZ * chunkDepth + localZ

        val zone = generation.zones.sampleZone(ctx, worldX, worldZ)

        val biomeBlend = zone.biomes.sampleBiomeBlend(ctx, worldX, worldZ)
        //cacheMap[worldX to worldZ] = biomeBlend
        val surfaceY = findSurfaceY(ctx, biomeBlend, worldX, worldZ)

        for (y in (maxHeight - 1) downTo minHeight) {
          val terrainMacro = generation.blendedBiomeDensity(ctx, biomeBlend, worldX, y, worldZ).finalDensity()

          val detail = ctx.noise.get(BaseNoiseKeys.TerrainDetail).noise3D(worldX,  y, worldZ) * 3.0
          val terrainFinal = terrainMacro + detail

          val caveCarve = generation.blendedBiomeCarve(ctx, biomeBlend, worldX, y, worldZ, surfaceY, terrainFinal)
          val caveAdd   = generation.blendedBiomeAdd(ctx, biomeBlend, worldX, y, worldZ, surfaceY, terrainFinal)//terrainMacro

          val finalDensity = terrainFinal - caveCarve + caveAdd

          val isSolid = finalDensity > 0.0
          val depthBelowSurface = surfaceY - y

          val materialContext = BukkitMaterialContext(
            ctx,
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

          if (chosenMaterial != BlockData.NONE) {
            ctx.chunkContext.setBlock(localX, y, localZ, chosenMaterial)
          }
        }
      }
    }

    structures.runForChunk(ctx, chunkX, chunkZ)

    decorations.runAllPasses(ctx, chunkX, chunkZ) { wx, wz ->
      //cacheMap[wx to wz]!!
      val zone = generation.zones.sampleZone(ctx, wx, wz)
      zone.biomes.sampleBiomeBlend(ctx, wx, wz)
    }
  }
}
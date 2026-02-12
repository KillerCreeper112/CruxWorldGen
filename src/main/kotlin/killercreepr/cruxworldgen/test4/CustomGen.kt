package killercreepr.cruxworldgen.test4

import killercreepr.cruxworldgen.test3.NoiseBank
import killercreepr.cruxworldgen.test4.info.ChunkContext
import killercreepr.cruxworldgen.test4.info.GenContext
import killercreepr.cruxworldgen.test4.info.SectionCtx
import killercreepr.cruxworldgen.test4.info.WorldContext
import org.bukkit.Material
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.WorldInfo
import java.util.Random

class CustomGen(
  val chunkSizeX : Int,
  val chunkSizeZ : Int,
  val pipeline: GenPipeline
) : ChunkGenerator() {
  lateinit var noiseBank : NoiseBank

  override fun generateNoise(
    worldInfo: WorldInfo,
    random: Random,
    chunkX: Int,
    chunkZ: Int,
    chunkData: ChunkData
  ) {
    val seed = worldInfo.seed
    val minY = chunkData.minHeight
    val maxY = chunkData.maxHeight

    if (!::noiseBank.isInitialized) {
      noiseBank = NoiseBank(seed)
    }

    val genCtx = GenContext(
      WorldContext(seed),
      random, chunkX, chunkZ, ChunkContext(
      chunkData.minHeight, chunkData.maxHeight
    ), noiseBank)

    for(x in 0 until chunkSizeX){
      val worldX = chunkX * chunkSizeX + x
      for(z in 0 until chunkSizeZ){
        val worldZ = chunkZ * chunkSizeZ + z

        for(y in maxY downTo minY){

          val sectionCtx = SectionCtx(genCtx.worldContext, noiseBank, genCtx.chunkContext)
          val density = pipeline.density(genCtx, worldX, y, worldZ)
          val result = pipeline.resolver.selectBiomes(sectionCtx, x, z)
          if(result.isEmpty) continue

          val dominateSection = result.dominateSection
          val dominateWeight = result.dominateWeight

          val surfaceCtx = object : SurfaceContext(genCtx, density, dominateWeight){
            override fun setBlock(material: Material) {
              chunkData.setBlock(worldX, y, worldZ, material)
            }
          }

          dominateSection.surface().applyTo(surfaceCtx)
        }

      }
    }
  }
}
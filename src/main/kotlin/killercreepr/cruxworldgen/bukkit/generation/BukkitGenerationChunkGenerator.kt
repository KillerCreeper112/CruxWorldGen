package killercreepr.cruxworldgen.bukkit.generation

import fr.maxlego08.zauctionhouse.zcore.utils.DefaultFontInfo
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.decor.DecorationPipeline
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.generation.GenerationPipeline
import killercreepr.cruxworldgen.api.noise.NoiseBank
import killercreepr.cruxworldgen.api.structure.StructurePipeline
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import killercreepr.cruxworldgen.bukkit.context.BukkitChunkContext
import killercreepr.cruxworldgen.bukkit.context.BukkitGenerateContext
import killercreepr.cruxworldgen.bukkit.context.BukkitMaterialContext
import killercreepr.cruxworldgen.bukkit.context.BukkitWorldContext
import killercreepr.cruxworldgen.core.noise.BaseNoiseKeys
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate.solid
import org.bukkit.Bukkit
import org.bukkit.Material
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
      val terrainMacro = generation.blendedBiomeDensity(ctx, biomeBlend, worldX, y, worldZ).finalDensity()

      val detail = ctx.noise.get(BaseNoiseKeys.TerrainDetail).noise3D(worldX,  y, worldZ) * 3.0
      val terrainFinal = terrainMacro + detail
      if(terrainFinal > 0.0) return y
      /*val terrainDensity = generation.terrainDensityNoCaves(ctx, biomeBlend, worldX, y, worldZ)
      if (terrainDensity > 0.0) return y*/
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

    val minY = ctx.chunkContext.minHeight
    val maxY = ctx.chunkContext.maxHeight - 1
    val H = maxY - minY + 1

    fun vid(x: Int, z: Int, y: Int) = (x and 15) + ((z and 15) shl 4) + ((y - minY) shl 8)

    val density = DoubleArray(16 * 16 * H)
    val surfaceYArr = IntArray(16 * 16)

    for (localX in 0 until chunkWidth) {
      for (localZ in 0 until chunkDepth) {

        val worldX = chunkX * chunkWidth + localX
        val worldZ = chunkZ * chunkDepth + localZ

        val zone = generation.zones.sampleZone(ctx, worldX, worldZ)

        val biomeBlend = zone.biomes.sampleBiomeBlend(ctx, worldX, worldZ)

        val minY = ctx.chunkContext.minHeight
        val maxY = ctx.chunkContext.maxHeight - 1
        val height = maxY - minY + 1

        val airAbove = IntArray(height)
        val airBelow = IntArray(height)

        val col = DoubleArray(H)
        val surfaceY = findSurfaceY(ctx, biomeBlend, worldX, worldZ)
        surfaceYArr[localX + (localZ shl 4)] = surfaceY

        for (y in maxY downTo minY) {
          val terrainMacro = generation.blendedBiomeDensity(ctx, biomeBlend, worldX, y, worldZ).finalDensity()
          val detail = ctx.noise.get(BaseNoiseKeys.TerrainDetail).noise3D(worldX, y, worldZ) * 3.0
          val terrainFinal = terrainMacro + detail

          val caveCarve = generation.blendedBiomeCarve(ctx, biomeBlend, worldX, y, worldZ, surfaceY, terrainFinal)
          val caveAdd   = generation.blendedBiomeAdd(ctx, biomeBlend, worldX, y, worldZ, surfaceY, terrainFinal)

          val finalDensity = terrainFinal - caveCarve + caveAdd

          val iy = y - minY
          col[iy] = finalDensity
          density[vid(localX, localZ, y)] = finalDensity
        }

        var run = 0
        for (y in maxY downTo minY) {
          val iy = y - minY
          if (col[iy] <= 0.0) run++ else { airAbove[iy] = run; run = 0 }
        }

        run = 0
        for (y in minY..maxY) {
          val iy = y - minY
          if (col[iy] <= 0.0) run++ else { airBelow[iy] = run; run = 0 }
        }

        val sea = ctx.chunkContext.seaLevel
        val columnUnderwater = surfaceY < sea

        for (y in maxY downTo minY) {
          val iy = y - minY
          val d = col[iy]
          val isSolid = d > 0.0
          //if (density[idx] <= 0.0) continue

          val depthBelowSurface = surfaceY - y

          val isUnderwater =
            columnUnderwater &&
              y <= sea &&
              airAbove[iy] >= 8 // "open water column" heuristic; prevents underwater-cave misflags

          val isSeaFloor =
            (surfaceY < sea) &&
              (y == surfaceY) && (airAbove[iy] > 0)

          val materialContext = BukkitMaterialContext(
            ctx,
            worldX = worldX,
            y = y,
            worldZ = worldZ,
            isSolid = isSolid,
            surfaceY = surfaceY,
            depthBelowSurface = depthBelowSurface,
            airBlocksAbove = airAbove[iy],
            caveAirBlocksBelow = airBelow[iy],
            isUnderwater = isUnderwater,
            isSeaFloor = isSeaFloor,
          )

          val chosenMaterial = biomeBlend.primaryBiome().materialProvider.chooseMaterial(materialContext)
          if (chosenMaterial != BlockData.NONE) {
            ctx.chunkContext.setBlock(localX, y, localZ, chosenMaterial)
          }
        }

        /*val worldX = chunkX * chunkWidth + localX
        val worldZ = chunkZ * chunkDepth + localZ

        val zone = generation.zones.sampleZone(ctx, worldX, worldZ)

        val biomeBlend = zone.biomes.sampleBiomeBlend(ctx, worldX, worldZ)
        //cacheMap[worldX to worldZ] = biomeBlend
        val surfaceY = findSurfaceY(ctx, biomeBlend, worldX, worldZ)

        for (y in (ctx.chunkContext.maxHeight - 1) downTo ctx.chunkContext.minHeight) {
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
        }*/
      }
    }

    fillFluids(ctx, chunkX, chunkZ, density, surfaceYArr, minY, maxY)

    structures.runForChunk(ctx, chunkX, chunkZ)

    decorations.runAllPasses(ctx, chunkX, chunkZ) { wx, wz ->
      //cacheMap[wx to wz]!!
      val zone = generation.zones.sampleZone(ctx, wx, wz)
      zone.biomes.sampleBiomeBlend(ctx, wx, wz)
    }
  }

  fun fillFluids(
    ctx: BukkitGenerateContext,
    chunkX: Int, chunkZ: Int,
    density: DoubleArray,
    surfaceY: IntArray,
    minY: Int, maxY: Int
  ) {
    val sea = ctx.chunkContext.seaLevel
    val H = maxY - minY + 1

    fun vid(x:Int, z:Int, y:Int) =
      (x and 15) + ((z and 15) shl 4) + ((y - minY) shl 8)

    val oceanConn = BooleanArray(16 * 16 * H)
    val q = IntArray(16 * 16 * H)
    var qh = 0
    var qt = 0

    val WATER = BukkitBlockResolver.INSTANCE.resolve(Material.WATER)

    // 1) Fill surface water columns up to sea and seed BFS
    for (x in 0 until 16) for (z in 0 until 16) {
      val sY = surfaceY[x + (z shl 4)]
      if (sY >= sea) continue

      val top = minOf(sea, maxY)
      val start = maxOf(sY + 1, minY)

      for (y in start..top) {
        val i = vid(x, z, y)
        if (density[i] <= 0.0 && !oceanConn[i]) {
          oceanConn[i] = true
          ctx.chunkContext.setBlock(x, y, z, WATER)
          q[qt++] = i
        }
      }
    }

    // 2) BFS into air below sea level
    while (qh < qt) {
      val i = q[qh++]
      val y = (i shr 8) + minY
      if (y > sea) continue

      val x = i and 15
      val z = (i shr 4) and 15

      fun tryPush(nx:Int, ny:Int, nz:Int) {
        if (nx !in 0..15 || nz !in 0..15) return
        if (ny !in minY..minOf(sea, maxY)) return

        val ni = vid(nx, nz, ny)
        if (oceanConn[ni]) return
        if (density[ni] > 0.0) return // solid blocks stop water

        oceanConn[ni] = true
        ctx.chunkContext.setBlock(nx, ny, nz, WATER)
        q[qt++] = ni
      }

      tryPush(x + 1, y, z)
      tryPush(x - 1, y, z)
      tryPush(x, y + 1, z)
      tryPush(x, y - 1, z)
      tryPush(x, y, z + 1)
      tryPush(x, y, z - 1)
    }

    // 3) Aquifers later (fill where !oceanConn and density<=0)
  }


}
package killercreepr.cruxworldgen.bukkit.generation

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.terrain.Terrain3D
import killercreepr.cruxworldgen.api.decor.DecorationPipeline
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.generation.GenerationPipeline
import killercreepr.cruxworldgen.api.generation.chunk.ChunkSampler
import killercreepr.cruxworldgen.api.generation.chunk.SampledChunk
import killercreepr.cruxworldgen.api.noise.NoiseBank
import killercreepr.cruxworldgen.api.signal.SignalHandler
import killercreepr.cruxworldgen.api.signal.SignalView
import killercreepr.cruxworldgen.api.structure.StructurePipeline
import killercreepr.cruxworldgen.api.util.MathUtil.cornerColumnIndex
import killercreepr.cruxworldgen.api.util.MathUtil.cornerIndex
import killercreepr.cruxworldgen.bukkit.biome.BukkitBiome
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockData
import killercreepr.cruxworldgen.bukkit.context.BukkitMaterialContext
import killercreepr.cruxworldgen.bukkit.region.BukkitLimitedRegion
import killercreepr.cruxworldgen.core.feature.FeaturePipeline
import killercreepr.cruxworldgen.core.noise.BaseNoiseKeys
import killercreepr.cruxworldgen.core.signal.SimpleSignalWriter
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Biome
import org.bukkit.generator.BiomeProvider
import org.bukkit.generator.BlockPopulator
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.LimitedRegion
import org.bukkit.generator.WorldInfo
import java.util.*
import java.util.concurrent.TimeUnit

data class WorldDetails(
  val seaLevel: Int,
  val chunkWidth: Int,
  val chunkDepth: Int
)

class BukkitGenerationChunkGenerator(
  val generation: GenerationPipeline,
  val decorations: DecorationPipeline,
  val structures: StructurePipeline,
  val noise: NoiseBank,
  val worldDetails: WorldDetails,
  val features: FeaturePipeline,
  val chunkSampler: ChunkSampler
) : ChunkGenerator() {
  val bukkitBiomes: List<org.bukkit.block.Biome>

  init {
    val buildingBiomes = mutableListOf<org.bukkit.block.Biome>()
    generation.zones.zones.forEach { zone ->
      zone.biomes.biomes.forEach { biome ->
        if (biome !is BukkitBiome) return@forEach
        val bukkit = biome.toBukkitBiome()
        if (!buildingBiomes.contains(bukkit)) buildingBiomes.add(bukkit)
      }
    }
    bukkitBiomes = buildingBiomes
  }

  fun setBlock(chunkData: ChunkData, x: Int, y: Int, z: Int, block: BlockData) {
    (block as? BukkitBlockData) ?: error("$block is not a BukkitBlockData")
    block.setAt(chunkData, x, y, z)
  }
  /*fun findSurfaceY(
    ctx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    worldZ: Int
  ): Int {
    val minY = ctx.chunkContext.minHeight
    val maxY = ctx.chunkContext.maxHeight - 1
    val terrainDetailNoise = ctx.noise.get(BaseNoiseKeys.TerrainDetail)

    fun isSurfaceSolid(y: Int): Boolean {
      val terrainMacro =
        generation.blendedBiomeDensity(ctx, biomeBlend, worldX, y, worldZ, SignalHandler.DUMMY).finalDensity()
      val detail = terrainDetailNoise.noise3D(worldX, y, worldZ) * 3.0
      val terrainFinal = terrainMacro + detail
      if (terrainFinal > 0.0) return true

      val terrainDensity =
        generation.terrainDensityNoCaves(ctx, biomeBlend, worldX, y, worldZ, SignalHandler.DUMMY)
      return terrainDensity > 0.0
    }

    val step = 4
    var y = maxY

    while (y >= minY) {
      if (isSurfaceSolid(y)) {
        val refineTop = minOf(maxY, y + step - 1)
        for (yy in refineTop downTo (y + 1)) {
          if (isSurfaceSolid(yy)) return yy
        }
        return y
      }
      y -= step
    }

    return minY
  }*/

  //val cache = ConcurrentHashMap<Long, SampledChunk>(5000)
  val cache: Cache<Long, CachedChunk> = CacheBuilder.newBuilder()
    .maximumSize(5000)
    .expireAfterAccess(2, TimeUnit.MINUTES)
    .concurrencyLevel(4)
    .build()

  data class CachedChunk(
    val chunk : SampledChunk,
    var signalWriter : SignalHandler
  )

  fun chunkXFromWorld(worldX: Int, chunkWidth: Int = worldDetails.chunkWidth): Int =
    Math.floorDiv(worldX, chunkWidth)

  fun chunkZFromWorld(worldZ: Int, chunkDepth: Int = worldDetails.chunkDepth): Int =
    Math.floorDiv(worldZ, chunkDepth)

  fun getOrCreateCache(
    worldInfo: WorldInfo,
    random: Random,
    chunkX: Int,
    chunkZ: Int,
    signalWriter: () -> SignalHandler = { SimpleSignalWriter(mutableMapOf()) }
  ): CachedChunk {
    val key = chunkKey(chunkX, chunkZ)
    return cache.get(key) {
      val writer = signalWriter.invoke()
      CachedChunk(
        chunkSampler.sample(worldInfo, random, chunkX, chunkZ, writer),
        writer
      )
    }
  }

  override fun generateNoise(
    worldInfo: WorldInfo,
    random: Random,
    chunkX: Int,
    chunkZ: Int,
    chunkData: ChunkData
  ) {
    val cachedChunk = getOrCreateCache(worldInfo, random, chunkX, chunkZ)
    val sampledChunk = cachedChunk.chunk
    val signalWriter = cachedChunk.signalWriter
    cachedChunk.signalWriter = SignalHandler.DUMMY

    writeSampledTerrain(
      chunkData,
      chunkX, chunkZ,
      sampledChunk, signalWriter,
      sampledChunk.terrainSnapshot.terrain3D
    )

    fillSampledFluids(
      worldInfo,
      chunkData,
      sampledChunk,
      chunkX,
      chunkZ
    )

    /*writeSampledTerrainToChunk(
      chunkData,
      chunkX, chunkZ,
      sampledChunk, signalWriter,
      sampledChunk.terrainSnapshot.terrain3D
    )
    fillSampledFluids(
      worldInfo,
      chunkData,
      sampledChunk,
      chunkX,
      chunkZ
    )*/
  }

  fun writeSampledTerrain(
    chunkData: ChunkData,
    chunkX: Int,
    chunkZ: Int,
    sampledChunk: SampledChunk,
    signalWriter: SignalView,
    terrain3D: Terrain3D
  ){
    val ctx = sampledChunk.ctx
    val chunkWidth = worldDetails.chunkWidth
    val chunkDepth = worldDetails.chunkDepth
    val minY = ctx.chunkContext.minHeight
    val maxY = ctx.chunkContext.maxHeight - 1
    val height = maxY - minY + 1

    val biomeCellSize = chunkSampler.biomeCellSize
    val biomeCellCountX = chunkWidth / biomeCellSize
    val biomeCellCountZ = chunkDepth / biomeCellSize
    val biomeCellCountY = (height + biomeCellSize - 1) / biomeCellSize

    for (localX in 0 until chunkWidth) {
      for (localZ in 0 until chunkDepth) {
        val worldX = chunkX * chunkWidth + localX
        val worldZ = chunkZ * chunkDepth + localZ

        val columnIndex = columnIndex(localX, localZ, chunkWidth)
        val surfaceY = sampledChunk.surfaceYByBlockColumn[columnIndex]

        val airAbove = IntArray(height)
        val airBelow = IntArray(height)

        val caveAirAbove = IntArray(height)
        val caveAirBelow = IntArray(height)

        computeAirRuns(
          sampledChunk.densityByBlock, localX, localZ, minY, maxY, chunkWidth, chunkDepth, airAbove, airBelow,
          caveAirAbove, caveAirBelow, terrain3D
        )

        val seaLevel = ctx.chunkContext.seaLevel
        val columnUnderwater = surfaceY < seaLevel

        var surfaceDepth = 0
        var airRun = 0

        val cellX = (localX / biomeCellSize).coerceIn(0, biomeCellCountX - 1)
        val cellZ = (localZ / biomeCellSize).coerceIn(0, biomeCellCountZ - 1)

        val localCellOriginX = cellX * biomeCellSize
        val localCellOriginZ = cellZ * biomeCellSize
        for (blockY in maxY downTo minY) {
          val cellY = ((blockY - minY) / biomeCellSize).coerceIn(0, biomeCellCountY - 1)

          val blockIndex = blockIndex(localX, localZ, blockY, minY, chunkWidth, chunkDepth)
          val cornerIndex = cornerIndex(cellX, cellZ, cellY, biomeCellCountX, biomeCellCountZ)
          val cornerColumnIndex = cornerColumnIndex(cellX, cellZ, biomeCellCountX)

          val airAboveHere = airRun

          val solidWithoutCaves = sampledChunk.solidNoCavesByBlock[blockIndex]
          if (solidWithoutCaves) {
            surfaceDepth++
            airRun = 0
          } else {
            airRun++
            surfaceDepth = -1
          }

          val density = sampledChunk.densityByBlock[blockIndex]
          val materialBiome = sampledChunk.primaryBiomeByBlock[blockIndex]!!

          val iy = blockY - minY
          val depthBelowSurface = surfaceY - blockY

          val isUnderwater =
            columnUnderwater &&
              blockY <= seaLevel &&
              airAbove[iy] >= 8

          val isSolid = density > 0.0

          val depthFromSeaFloor =
            if (columnUnderwater) surfaceY - blockY else -1

          val materialContext = BukkitMaterialContext(
            ctx,
            worldX = chunkX * chunkWidth + localX,
            y = blockY,
            worldZ = chunkZ * chunkDepth + localZ,
            isSolid = isSolid,
            surfaceY = surfaceY,
            depthBelowSurface = depthBelowSurface,
            airBlocksAbove = airAbove[iy],
            caveAirBlocksBelow = airBelow[iy],
            isUnderwater = isUnderwater,
            depthFromSeaFloor = depthFromSeaFloor,
            signalView = signalWriter,
            caveAirBlocksAbove = caveAirAbove[iy],
            solidWithoutCaves = sampledChunk.densityTerrainMacroByCorner[cornerIndex] > 0.0,
            surfaceDepth = surfaceDepth,
            airRun = airAboveHere,
          )

          val block = materialBiome.materialProvider.chooseMaterial(materialContext)
          if (block != BlockData.NONE) {
            setBlock(chunkData, localX, blockY, localZ, block)
          }
        }
      }
    }
  }

  fun computeAirRuns(
    densityByBlock: DoubleArray,
    localX: Int,
    localZ: Int,
    minBlockY: Int,
    maxBlockY: Int,
    chunkWidth: Int,
    chunkDepth: Int,
    airAbove: IntArray,
    airBelow: IntArray,
    caveAirAbove: IntArray,
    caveAirBelow: IntArray,
    terrain3D: Terrain3D
  ) {
    var run = 0
    for (blockY in maxBlockY downTo minBlockY) {
      val iy = blockY - minBlockY
      val blockIndex = blockIndex(localX, localZ, blockY, minBlockY, chunkWidth, chunkDepth)

      if (densityByBlock[blockIndex] <= 0.0) {
        run++
      } else {
        airAbove[iy] = run
        if(terrain3D.localIsCaveAir(localX, blockY, localZ)) caveAirAbove[iy] = run
        run = 0
      }
    }

    run = 0
    for (blockY in minBlockY..maxBlockY) {
      val iy = blockY - minBlockY
      val blockIndex = blockIndex(localX, localZ, blockY, minBlockY, chunkWidth, chunkDepth)

      if (densityByBlock[blockIndex] <= 0.0) {
        run++
      } else {
        airBelow[iy] = run
        if(terrain3D.localIsCaveAir(localX, blockY, localZ)) caveAirBelow[iy] = run
        run = 0
      }
    }
  }

  fun fillSampledFluids(
    worldInfo: WorldInfo,
    chunkData: ChunkData,
    sampledChunk: SampledChunk,
    chunkX: Int,
    chunkZ: Int
  ) {
    val ctx = sampledChunk.ctx
    val chunkWidth = worldDetails.chunkWidth
    val chunkDepth = worldDetails.chunkDepth

    val minBlockY = ctx.chunkContext.minHeight
    val maxBlockY = ctx.chunkContext.maxHeight - 1
    val seaLevel = ctx.chunkContext.seaLevel
    val cappedSeaLevel = minOf(seaLevel, maxBlockY)
    val chunkBlockHeight = maxBlockY - minBlockY + 1

    val chunkOriginWorldX = chunkX * chunkWidth
    val chunkOriginWorldZ = chunkZ * chunkDepth

    fun blockIndex(localX: Int, localZ: Int, blockY: Int): Int {
      val localY = blockY - minBlockY
      return (localY * chunkDepth + localZ) * chunkWidth + localX
    }

    fun columnIndex(localX: Int, localZ: Int): Int {
      return localZ * chunkWidth + localX
    }

    val totalBlocks = chunkWidth * chunkDepth * chunkBlockHeight
    val seaConnected = BooleanArray(totalBlocks)
    val queue = IntArray(totalBlocks)
    var head = 0
    var tail = 0

    fun isAir(localX: Int, localZ: Int, blockY: Int): Boolean {
      return sampledChunk.densityByBlock[blockIndex(localX, localZ, blockY)] <= 0.0
    }

    fun enqueue(localX: Int, localZ: Int, blockY: Int) {
      if (localX !in 0 until chunkWidth) return
      if (localZ !in 0 until chunkDepth) return
      if (blockY !in minBlockY..cappedSeaLevel) return

      val index = blockIndex(localX, localZ, blockY)
      if (seaConnected[index]) return
      if (!isAir(localX, localZ, blockY)) return

      seaConnected[index] = true
      queue[tail++] = index
    }

    fun decodeX(index: Int): Int = index % chunkWidth
    fun decodeZ(index: Int): Int = (index / chunkWidth) % chunkDepth
    fun decodeY(index: Int): Int = minBlockY + (index / (chunkWidth * chunkDepth))

    //
    // Seed from actual sea water inside this chunk
    //
    for (localX in 0 until chunkWidth) {
      for (localZ in 0 until chunkDepth) {
        val surfaceY = sampledChunk.surfaceYByBlockColumn[columnIndex(localX, localZ)]
        if (surfaceY >= seaLevel) continue

        val waterStartY = maxOf(surfaceY + 1, minBlockY)
        for (blockY in waterStartY..cappedSeaLevel) {
          enqueue(localX, localZ, blockY)
        }
      }
    }

    //
    // Seed from neighboring ocean columns only at the chunk boundary
    //
    for (localZ in 0 until chunkDepth) {
      val worldZ = chunkOriginWorldZ + localZ

      val westSurfaceY = surfaceYAt(worldInfo, chunkOriginWorldX - 1, worldZ)
      if (westSurfaceY < seaLevel) {
        val startY = maxOf(westSurfaceY + 1, minBlockY)
        for (blockY in startY..cappedSeaLevel) {
          enqueue(0, localZ, blockY)
        }
      }

      val eastSurfaceY = surfaceYAt(worldInfo, chunkOriginWorldX + chunkWidth, worldZ)
      if (eastSurfaceY < seaLevel) {
        val startY = maxOf(eastSurfaceY + 1, minBlockY)
        for (blockY in startY..cappedSeaLevel) {
          enqueue(chunkWidth - 1, localZ, blockY)
        }
      }
    }

    for (localX in 0 until chunkWidth) {
      val worldX = chunkOriginWorldX + localX

      val northSurfaceY = surfaceYAt(worldInfo, worldX, chunkOriginWorldZ - 1)
      if (northSurfaceY < seaLevel) {
        val startY = maxOf(northSurfaceY + 1, minBlockY)
        for (blockY in startY..cappedSeaLevel) {
          enqueue(localX, 0, blockY)
        }
      }

      val southSurfaceY = surfaceYAt(worldInfo, worldX, chunkOriginWorldZ + chunkDepth)
      if (southSurfaceY < seaLevel) {
        val startY = maxOf(southSurfaceY + 1, minBlockY)
        for (blockY in startY..cappedSeaLevel) {
          enqueue(localX, chunkDepth - 1, blockY)
        }
      }
    }

    //
    // Flood only inside this chunk
    //
    while (head < tail) {
      val index = queue[head++]
      val localX = decodeX(index)
      val localZ = decodeZ(index)
      val blockY = decodeY(index)

      enqueue(localX + 1, localZ, blockY)
      enqueue(localX - 1, localZ, blockY)
      enqueue(localX, localZ + 1, blockY)
      enqueue(localX, localZ - 1, blockY)
      enqueue(localX, localZ, blockY + 1)
      enqueue(localX, localZ, blockY - 1)
    }

    //
    // Place water
    //
    for (localX in 0 until chunkWidth) {
      for (localZ in 0 until chunkDepth) {
        for (blockY in minBlockY..cappedSeaLevel) {
          val index = blockIndex(localX, localZ, blockY)
          if (!seaConnected[index]) continue
          if (sampledChunk.densityByBlock[index] > 0.0) continue

          chunkData.setBlock(localX, blockY, localZ, Material.WATER)
        }
      }
    }
  }

  fun surfaceYAt(worldInfo: WorldInfo, worldX: Int, worldZ: Int): Int {
    val queryChunkX = Math.floorDiv(worldX, worldDetails.chunkWidth)
    val queryChunkZ = Math.floorDiv(worldZ, worldDetails.chunkDepth)

    val random = Random(worldInfo.seed)
    val cache = getOrCreateCache(
      worldInfo, random, queryChunkX, queryChunkZ
    )
    return cache.chunk.surfaceYByBlockColumn[columnIndex(
      localXFromWorld(worldX, worldDetails.chunkWidth),
      localZFromWorld(worldZ, worldDetails.chunkDepth),
      worldDetails.chunkWidth
    )]
  }

  /*fun writeSampledTerrain(
    chunkData: ChunkData,
    chunkX: Int,
    chunkZ: Int,
    sampledChunk: SampledChunk,
    signalWriter: SignalView,
    terrain3D: Terrain3D
  ) {
    val ctx = sampledChunk.ctx
    val chunkWidth = worldDetails.chunkWidth
    val chunkDepth = worldDetails.chunkDepth
    val minY = ctx.chunkContext.minHeight
    val maxY = ctx.chunkContext.maxHeight - 1
    val height = maxY - minY + 1

    val biomeCellSize = chunkSampler.biomeCellSize
    val biomeCellCountX = chunkWidth / biomeCellSize
    val biomeCellCountZ = chunkDepth / biomeCellSize
    val biomeCellCountY = (height + biomeCellSize - 1) / biomeCellSize

    for (localX in 0 until chunkWidth) {
      for (localZ in 0 until chunkDepth) {
        val columnIndex = columnIndex(localX, localZ, chunkWidth)
        val surfaceY = sampledChunk.surfaceY[columnIndex]

        val airAbove = IntArray(height)
        val airBelow = IntArray(height)

        val caveAirAbove = IntArray(height)
        val caveAirBelow = IntArray(height)

        computeAirRuns(sampledChunk.density, localX, localZ, minY, maxY, chunkWidth, chunkDepth, airAbove, airBelow,
          caveAirAbove, caveAirBelow, terrain3D)

        val seaLevel = ctx.chunkContext.seaLevel
        val columnUnderwater = surfaceY < seaLevel

        var surfaceDepth = 0
        var airRun = 0

        val cellX = (localX / biomeCellSize).coerceIn(0, biomeCellCountX - 1)
        val cellZ = (localZ / biomeCellSize).coerceIn(0, biomeCellCountZ - 1)
        val localCellOriginX = cellX * biomeCellSize
        val localCellOriginZ = cellZ * biomeCellSize
        for (blockY in maxY downTo minY) {
          val cellY = ((blockY - minY) / biomeCellSize).coerceIn(0, biomeCellCountY - 1)

          val blockIndex = blockIndex(localX, localZ, blockY, minY, chunkWidth, chunkDepth)
          val cornerIndex = cornerIndex(cellX, cellZ, cellY, biomeCellCountX, biomeCellCountZ)

          val c000 = cornerIndex(cellX,     cellZ,     cellY,     biomeCellCountX, biomeCellCountZ)
          val c100 = cornerIndex(cellX + 1, cellZ,     cellY,     biomeCellCountX, biomeCellCountZ)
          val c010 = cornerIndex(cellX,     cellZ + 1, cellY,     biomeCellCountX, biomeCellCountZ)
          val c110 = cornerIndex(cellX + 1, cellZ + 1, cellY,     biomeCellCountX, biomeCellCountZ)

          val c001 = cornerIndex(cellX,     cellZ,     cellY + 1, biomeCellCountX, biomeCellCountZ)
          val c101 = cornerIndex(cellX + 1, cellZ,     cellY + 1, biomeCellCountX, biomeCellCountZ)
          val c011 = cornerIndex(cellX,     cellZ + 1, cellY + 1, biomeCellCountX, biomeCellCountZ)
          val c111 = cornerIndex(cellX + 1, cellZ + 1, cellY + 1, biomeCellCountX, biomeCellCountZ)
          val localCellOriginY = minY + cellY * biomeCellSize

          val tx = (((localX - localCellOriginX).toDouble() + 0.5) / biomeCellSize.toDouble()).coerceIn(0.0, 1.0)
          val tz = (((localZ - localCellOriginZ).toDouble() + 0.5) / biomeCellSize.toDouble()).coerceIn(0.0, 1.0)
          val ty = (((blockY - localCellOriginY).toDouble() + 0.5) / biomeCellSize.toDouble()).coerceIn(0.0, 1.0)

          val density = Curve.trilerp(
            sampledChunk.terrainMacroByCell[c000],
            sampledChunk.terrainMacroByCell[c100],
            sampledChunk.terrainMacroByCell[c010],
            sampledChunk.terrainMacroByCell[c110],
            sampledChunk.terrainMacroByCell[c001],
            sampledChunk.terrainMacroByCell[c101],
            sampledChunk.terrainMacroByCell[c011],
            sampledChunk.terrainMacroByCell[c111],
            tx, ty, tz
          ) +
            Curve.trilerp(
              sampledChunk.caveMacroByCell[c000],
              sampledChunk.caveMacroByCell[c100],
              sampledChunk.caveMacroByCell[c010],
              sampledChunk.caveMacroByCell[c110],
              sampledChunk.caveMacroByCell[c001],
              sampledChunk.caveMacroByCell[c101],
              sampledChunk.caveMacroByCell[c011],
              sampledChunk.caveMacroByCell[c111],
              tx, ty, tz
            )

          //val density = sampledChunk.density[blockIndex]
          val isSolid = density > 0.0

          val airAboveHere = airRun

          val solidWithoutCaves = sampledChunk.solidNoCavesByBlock[blockIndex]
          if (solidWithoutCaves) {
            surfaceDepth++
            airRun = 0
          } else {
            airRun++
            surfaceDepth = -1
          }

          if(!isSolid) continue //todo this is good for performance but may need to change to provide more control for biomes- guess we see

          if(true){

            setBlock(chunkData, localX, blockY, localZ, BukkitBlockAdapter.resolver().resolve(Material.STONE))
            continue
          }

          val biome = sampledChunk.dominantBiomeByBlock[blockIndex] ?: continue

          val iy = blockY - minY
          val depthBelowSurface = surfaceY - blockY

          val isUnderwater =
            columnUnderwater &&
              blockY <= seaLevel &&
              airAbove[iy] >= 8

          val depthFromSeaFloor =
            if (columnUnderwater) surfaceY - blockY else -1

          val materialContext = BukkitMaterialContext(
            ctx,
            worldX = chunkX * chunkWidth + localX,
            y = blockY,
            worldZ = chunkZ * chunkDepth + localZ,
            isSolid = isSolid,
            surfaceY = surfaceY,
            depthBelowSurface = depthBelowSurface,
            airBlocksAbove = airAbove[iy],
            caveAirBlocksBelow = airBelow[iy],
            isUnderwater = isUnderwater,
            depthFromSeaFloor = depthFromSeaFloor,
            signalView = signalWriter,
            caveAirBlocksAbove = caveAirAbove[iy],
            solidWithoutCaves = sampledChunk.solidNoCavesByBlock[blockIndex],
            surfaceDepth = surfaceDepth,
            airRun = airAboveHere,
          )

          val block = biome.materialProvider.chooseMaterial(materialContext)
          if (block != BlockData.NONE) {
            setBlock(chunkData, localX, blockY, localZ, block)
          }
        }
      }
    }
  }

  fun surfaceYAt(worldInfo: WorldInfo, worldX: Int, worldZ: Int): Int {
    val queryChunkX = Math.floorDiv(worldX, worldDetails.chunkWidth)
    val queryChunkZ = Math.floorDiv(worldZ, worldDetails.chunkDepth)

    val cache = cache.getIfPresent(chunkKey(queryChunkX, queryChunkZ))
    if(cache != null) return cache.chunk.surfaceY[columnIndex(localXFromWorld(worldX, worldDetails.chunkWidth), localZFromWorld(worldZ, worldDetails.chunkDepth), worldDetails.chunkWidth)]

    val random = Random(worldInfo.seed)
    val ctx = BukkitGenerateContext(
      BukkitWorldContext(worldInfo),
      random,
      queryChunkX,
      queryChunkZ,
      BukkitChunkContext(
        worldInfo.minHeight,
        worldInfo.maxHeight,
        worldDetails.seaLevel,
        worldDetails.chunkWidth,
        worldDetails.chunkDepth
      ),
      noise
    )

    val zone = generation.zones.sampleZone(ctx, worldX, worldZ)
    val blend = zone.biomes.sampleBiomeBlend(ctx, worldX, worldZ)
    return findSurfaceY(ctx, blend, worldX, worldZ)
  }*/

  /*

  fun computeAirRuns(
    densityByBlock: DoubleArray,
    localX: Int,
    localZ: Int,
    minBlockY: Int,
    maxBlockY: Int,
    chunkWidth: Int,
    chunkDepth: Int,
    airAbove: IntArray,
    airBelow: IntArray,
    caveAirAbove: IntArray,
    caveAirBelow: IntArray,
    terrain3D: Terrain3D
  ) {
    var run = 0
    for (blockY in maxBlockY downTo minBlockY) {
      val iy = blockY - minBlockY
      val blockIndex = blockIndex(localX, localZ, blockY, minBlockY, chunkWidth, chunkDepth)

      if (densityByBlock[blockIndex] <= 0.0) {
        run++
      } else {
        airAbove[iy] = run
        if(terrain3D.localIsCaveAir(localX, blockY, localZ)) caveAirAbove[iy] = run
        run = 0
      }
    }

    run = 0
    for (blockY in minBlockY..maxBlockY) {
      val iy = blockY - minBlockY
      val blockIndex = blockIndex(localX, localZ, blockY, minBlockY, chunkWidth, chunkDepth)

      if (densityByBlock[blockIndex] <= 0.0) {
        run++
      } else {
        airBelow[iy] = run
        if(terrain3D.localIsCaveAir(localX, blockY, localZ)) caveAirBelow[iy] = run
        run = 0
      }
    }
  }

  fun writeSampledTerrainToChunk(
    chunkData: ChunkData,
    chunkX: Int,
    chunkZ: Int,
    sampledChunk: SampledChunk,
    signalWriter: SignalView,
    terrain3D: Terrain3D
  ) {
    val ctx = sampledChunk.ctx
    val chunkWidth = worldDetails.chunkWidth
    val chunkDepth = worldDetails.chunkDepth
    val minY = ctx.chunkContext.minHeight
    val maxY = ctx.chunkContext.maxHeight - 1
    val height = maxY - minY + 1

    for (localX in 0 until chunkWidth) {
      for (localZ in 0 until chunkDepth) {
        val columnIndex = columnIndex(localX, localZ, chunkWidth)
        val surfaceY = sampledChunk.surfaceY[columnIndex]

        val airAbove = IntArray(height)
        val airBelow = IntArray(height)

        val caveAirAbove = IntArray(height)
        val caveAirBelow = IntArray(height)

        computeAirRuns(sampledChunk.density, localX, localZ, minY, maxY, chunkWidth, chunkDepth, airAbove, airBelow,
          caveAirAbove, caveAirBelow, terrain3D)

        val seaLevel = ctx.chunkContext.seaLevel
        val columnUnderwater = surfaceY < seaLevel

        var surfaceDepth = 0
        var airRun = 0
        for (blockY in maxY downTo minY) {
          val blockIndex = blockIndex(localX, localZ, blockY, minY, chunkWidth, chunkDepth)
          val density = sampledChunk.density[blockIndex]
          val isSolid = density > 0.0

          val airAboveHere = airRun

          val solidWithoutCaves = sampledChunk.solidNoCavesByBlock[blockIndex]
          if (solidWithoutCaves) {
            surfaceDepth++
            airRun = 0
          } else {
            airRun++
            surfaceDepth = -1
          }

          if(!isSolid) continue //todo this is good for performance but may need to change to provide more control for biomes- guess we see

          val biome = sampledChunk.dominantBiomeByBlock[blockIndex] ?: continue

          val iy = blockY - minY
          val depthBelowSurface = surfaceY - blockY

          val isUnderwater =
            columnUnderwater &&
              blockY <= seaLevel &&
              airAbove[iy] >= 8

          val depthFromSeaFloor =
            if (columnUnderwater) surfaceY - blockY else -1

          val materialContext = BukkitMaterialContext(
            ctx,
            worldX = chunkX * chunkWidth + localX,
            y = blockY,
            worldZ = chunkZ * chunkDepth + localZ,
            isSolid = isSolid,
            surfaceY = surfaceY,
            depthBelowSurface = depthBelowSurface,
            airBlocksAbove = airAbove[iy],
            caveAirBlocksBelow = airBelow[iy],
            isUnderwater = isUnderwater,
            depthFromSeaFloor = depthFromSeaFloor,
            signalView = signalWriter,
            caveAirBlocksAbove = caveAirAbove[iy],
            solidWithoutCaves = sampledChunk.solidNoCavesByBlock[blockIndex],
            surfaceDepth = surfaceDepth,
            airRun = airAboveHere,
          )

          val block = biome.materialProvider.chooseMaterial(materialContext)
          if (block != BlockData.NONE) {
            setBlock(chunkData, localX, blockY, localZ, block)
          }
        }
      }
    }
  }*/

   override fun getDefaultBiomeProvider(worldInfo: WorldInfo): BiomeProvider {
     return object : BiomeProvider() {
       override fun getBiome(
         worldInfo: WorldInfo,
         x: Int,
         y: Int,
         z: Int
       ): org.bukkit.block.Biome {
         val chunkX = chunkXFromWorld(x)
         val chunkZ = chunkZFromWorld(z)

         val cachedChunk = getOrCreateCache(
           worldInfo, Random(worldInfo.seed),
           chunkX, chunkZ
         )
         val cache = cachedChunk.chunk

         val localX = localXFromWorld(x, worldDetails.chunkWidth)
         val localZ = localZFromWorld(z, worldDetails.chunkDepth)
         val biome = cache.primaryBiomeByBlock[blockIndex(localX, localZ, y, worldInfo.minHeight, worldDetails.chunkWidth, worldDetails.chunkDepth)]
           ?: return Biome.PLAINS
         if(biome is BukkitBiome) return biome.toBukkitBiome()
         return Biome.PLAINS
       }

       override fun getBiomes(worldInfo: WorldInfo): List<org.bukkit.block.Biome?> = bukkitBiomes
     }
   }

  override fun getDefaultPopulators(world: World): List<BlockPopulator?> {
    return listOf(object : BlockPopulator() {
      override fun populate(
        worldInfo: WorldInfo,
        random: Random,
        chunkX: Int,
        chunkZ: Int,
        limitedRegion: LimitedRegion
      ) {
        val chunkWidth = worldDetails.chunkWidth
        val chunkDepth = worldDetails.chunkDepth
        val cachedChunk = getOrCreateCache(worldInfo, random, chunkX, chunkZ)
        val sampledChunk = cachedChunk.chunk
        val region = BukkitLimitedRegion(
          sampledChunk.ctx, limitedRegion,
          limitedRegion.buffer, limitedRegion.buffer,
          sampledChunk.ctx.chunkContext.minHeight, sampledChunk.ctx.chunkContext.maxHeight - 1,
          sampledChunk.terrainSnapshot
        )

        features.runForChunk(
          region, chunkX, chunkZ,
          { wx, wz ->
            val zone = generation.zones.sampleZone(region.ctx, wx, wz)
            zone.biomes.sampleBiomeBlend(region.ctx, wx, wz)
          },
          { wx, wy, wz ->
            sampledChunk.primaryBiomeByBlock[blockIndex(
              localXFromWorld(wx, chunkWidth),
              localZFromWorld(wz, chunkDepth),
              wy, sampledChunk.ctx.chunkContext.minHeight,
              chunkWidth, chunkDepth
            )]!!
          }
        )

        structures.runForChunk(region, chunkX, chunkZ)

        decorations.runAllPasses(region, chunkX, chunkZ,
          { wx, wz ->
            val zone = generation.zones.sampleZone(region.ctx, wx, wz)
            zone.biomes.sampleBiomeBlend(region.ctx, wx, wz)
          },
          {x,y,z ->
            sampledChunk.primaryBiomeByBlock[blockIndex(
              localXFromWorld(x, chunkWidth),
              localZFromWorld(z, chunkDepth),
              y, sampledChunk.ctx.chunkContext.minHeight, chunkWidth, chunkDepth
            )]!!
          },
          {x, z ->
            1//todo maybe not needed
          }
        )
      }
    })
  }

  fun blockIndex(localX: Int, localZ: Int, blockY: Int, minBlockY: Int, chunkWidth: Int, chunkDepth: Int): Int {
    val localY = blockY - minBlockY
    return (localY * chunkDepth + localZ) * chunkWidth + localX
  }

  private fun columnIndex(localX: Int, localZ: Int, chunkWidth: Int): Int {
    return localZ * chunkWidth + localX
  }

  private fun chunkKey(cx: Int, cz: Int): Long = (cx.toLong() shl 32) xor (cz.toLong() and 0xffffffffL)

  fun localXFromWorld(worldX: Int, chunkWidth: Int): Int =
    Math.floorMod(worldX, chunkWidth)

  fun localZFromWorld(worldZ: Int, chunkDepth: Int): Int =
    Math.floorMod(worldZ, chunkDepth)

  /*  override fun getBaseHeight(
      worldInfo: WorldInfo,
      random: Random,
      x: Int,
      z: Int,
      heightMap: HeightMap
    ): Int {
      if (true) return 0//todo implement back in for MC
      val chunkWidth = worldDetails.chunkWidth
      val chunkDepth = worldDetails.chunkDepth
      val chunkX = chunkXFromWorld(x, chunkWidth)
      val chunkZ = chunkZFromWorld(z, chunkDepth)

      val ctx = BukkitGenerateContext(
        BukkitWorldContext(worldInfo),
        random, chunkX, chunkZ,
        BukkitChunkContext(worldInfo.minHeight, worldInfo.maxHeight, worldDetails.seaLevel, chunkWidth, chunkDepth),
        noise
      )

      val zone = generation.zones.sampleZone(ctx, x, z)
      val biomeBlend = zone.biomes.sampleBiomeBlend(ctx, x, z)
      return findSurfaceY(ctx, biomeBlend, x, z)
    }*/

  /*fun localXFromWorld(worldX: Int, chunkWidth: Int): Int =
    Math.floorMod(worldX, chunkWidth)

  fun localZFromWorld(worldZ: Int, chunkDepth: Int): Int =
    Math.floorMod(worldZ, chunkDepth)

  override fun getDefaultPopulators(world: World): List<BlockPopulator?> {
    return listOf(object : BlockPopulator() {
      override fun populate(
        worldInfo: WorldInfo,
        random: Random,
        chunkX: Int,
        chunkZ: Int,
        limitedRegion: LimitedRegion
      ) {
        if(true) return//todo
        val chunkWidth = worldDetails.chunkWidth
        val chunkDepth = worldDetails.chunkDepth
        val cachedChunk = getOrCreateCache(worldInfo, random, chunkX, chunkZ)
        val sampledChunk = cachedChunk.chunk
        val region = BukkitLimitedRegion(
          sampledChunk.ctx, limitedRegion,
          limitedRegion.buffer, limitedRegion.buffer,
          sampledChunk.ctx.chunkContext.minHeight, sampledChunk.ctx.chunkContext.maxHeight - 1,
          sampledChunk.terrainSnapshot
        )

        features.runForChunk(
          region, chunkX, chunkZ,
          { wx, wz ->
            val zone = generation.zones.sampleZone(region.ctx, wx, wz)
            zone.biomes.sampleBiomeBlend(region.ctx, wx, wz)
          },
          { wx, wy, wz ->
            sampledChunk.dominantBiomeByBlock[blockIndex(
              localXFromWorld(wx, chunkWidth),
              localZFromWorld(wz, chunkDepth),
              wy, sampledChunk.ctx.chunkContext.minHeight,
              chunkWidth, chunkDepth
            )]!!
          }
        )

        structures.runForChunk(region, chunkX, chunkZ)

        decorations.runAllPasses(region, chunkX, chunkZ,
          { wx, wz ->
            val zone = generation.zones.sampleZone(region.ctx, wx, wz)
            zone.biomes.sampleBiomeBlend(region.ctx, wx, wz)
          },
          {x,y,z ->
            sampledChunk.dominantBiomeByBlock[blockIndex(
              localXFromWorld(x, chunkWidth),
              localZFromWorld(z, chunkDepth),
              y, sampledChunk.ctx.chunkContext.minHeight, chunkWidth, chunkDepth
            )]!!
          },
          {x, z ->
            1//todo maybe not needed
          }
          )
      }
    })
  }

  fun setBlock(chunkData: ChunkData, x: Int, y: Int, z: Int, block: BlockData) {
    (block as? BukkitBlockData) ?: error("$block is not a BukkitBlockData")
    block.setAt(chunkData, x, y, z)
  }

  fun vid(localX: Int, localZ: Int, y: Int, minY: Int) = (localX and 15) + ((localZ and 15) shl 4) + ((y - minY) shl 8)

  fun fillFluids(
    chunkData: ChunkData,
    ctx: BukkitGenerateContext,
    chunkX: Int, chunkZ: Int,
    density: DoubleArray,
    terrain2D: SimpleTerrain2D,
    minY: Int, maxY: Int
  ) {
    val sea = ctx.chunkContext.seaLevel
    val H = maxY - minY + 1

    val oceanConn = BooleanArray(16 * 16 * H)
    val q = IntArray(16 * 16 * H)
    var qh = 0
    var qt = 0

    val WATER = BukkitBlockResolver.INSTANCE.resolve(Material.WATER)
    val LAVA = BukkitBlockResolver.INSTANCE.resolve(Material.LAVA)
    val seaCap = minOf(sea, maxY)
    // 1) Fill surface water columns up to sea and seed BFS
    for (x in 0 until ctx.chunkContext.width) for (z in 0 until ctx.chunkContext.depth) {
      val worldX = ctx.toWorldX(x)
      val worldZ = ctx.toWorldZ(z)

      val sY = terrain2D.surfaceY(worldX, worldZ)
      val idx2D = terrain2D.idxUnsafe(worldX, worldZ)
      if (sY >= sea) {
        terrain2D.oceanFloorY[idx2D] = -1
        terrain2D.waterDepth[idx2D] = 0
        continue
      }

      terrain2D.oceanFloorY[idx2D] = sY

      val depth = (seaCap - sY).coerceAtLeast(0)
      terrain2D.waterDepth[idx2D] = depth

      val top = minOf(sea, maxY)
      val start = maxOf(sY + 1, minY)

      for (y in start..top) {
        val i = vid(x, z, y, minY)
        if (density[i] <= 0.0 && !oceanConn[i]) {
          oceanConn[i] = true
          setBlock(chunkData, x, y, z, WATER)
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

      fun tryPush(nx: Int, ny: Int, nz: Int) {
        if (nx !in 0..15 || nz !in 0..15) return
        if (ny !in minY..minOf(sea, maxY)) return

        val ni = vid(nx, nz, ny, minY)
        if (oceanConn[ni]) return
        if (density[ni] > 0.0) return // solid blocks stop water

        oceanConn[ni] = true
        setBlock(chunkData, nx, ny, nz, WATER)
        q[qt++] = ni
      }

      tryPush(x + 1, y, z)
      tryPush(x - 1, y, z)
      tryPush(x, y + 1, z)
      tryPush(x, y - 1, z)
      tryPush(x, y, z + 1)
      tryPush(x, y, z - 1)
    }

    // =========================
// 3) Aquifers (enclosed pockets, noise-driven, rarer)
// =========================

    val levelN = ctx.noise.get(BaseNoiseKeys.AquiferLevel2D)
    val depthN = ctx.noise.get(BaseNoiseKeys.AquiferDepth2D)
    val fillN = ctx.noise.get(BaseNoiseKeys.AquiferFill3D)
    val lavaN = ctx.noise.get(BaseNoiseKeys.AquiferLava3D)

    //val seaCap = minOf(sea, maxY)
    val baseWX = chunkX * ctx.chunkContext.width
    val baseWZ = chunkZ * ctx.chunkContext.depth

// Tunables (start here)
    val waterTableBase = sea - 18
    val waterTableAmp = 14.0

    val headroom = 6            // keep air gap under ceiling
    val minFillDepth = 2        // puddles
    val maxFillDepth = 12       // small underground lakes

    val minCavitySize = 48      // makes aquifers rarer & avoids tiny drips
    val minCavityHeight = 4     // skip thin cracks

    val fillThreshold = 0.90    // higher = fewer aquifers (0.75..0.88 good range)

    val lavaMaxY = minY + 28
    val lavaThreshold = 0.74

// "snap" reduces cross-chunk disagreements because we sample noises at a stable anchor
    val SNAP = 96               // 32/64/96/128; higher = more coherence

    fun snapToGrid(v: Int, snap: Int): Int = Math.floorDiv(v, snap) * snap + snap / 2

    val visited = BooleanArray(16 * 16 * H)
    val q2 = IntArray(16 * 16 * H)
    val comp = IntArray(16 * 16 * H)

    fun unpackX(i: Int) = i and 15
    fun unpackZ(i: Int) = (i shr 4) and 15
    fun unpackY(i: Int) = (i shr 8) + minY

    fun pushIfAir(i: Int, qtRef: IntArray, qtIdx: Int): Int {
      if (visited[i]) return qtIdx
      if (oceanConn[i]) return qtIdx
      if (density[i] > 0.0) return qtIdx
      visited[i] = true
      qtRef[qtIdx] = i
      return qtIdx + 1
    }

    for (x0 in 0 until 16) for (z0 in 0 until 16) {
      for (y0 in minY..seaCap) {
        val start = vid(x0, z0, y0, minY)
        if (visited[start]) continue
        if (oceanConn[start]) continue
        if (density[start] > 0.0) continue

        // ---- BFS this enclosed air component ----
        var qh2 = 0
        var qt2 = 0
        qt2 = pushIfAir(start, q2, qt2)

        var compSize = 0
        var minCompY = Int.MAX_VALUE
        var maxCompY = Int.MIN_VALUE
        var floorY = Int.MAX_VALUE

        // representative (world) centroid for anchoring noises
        var sumWX = 0
        var sumWZ = 0

        while (qh2 < qt2) {
          val i = q2[qh2++]
          comp[compSize++] = i

          val x = unpackX(i)
          val z = unpackZ(i)
          val y = unpackY(i)

          val wx = baseWX + x
          val wz = baseWZ + z
          sumWX += wx
          sumWZ += wz

          if (y < minCompY) minCompY = y
          if (y > maxCompY) maxCompY = y

          // floor: an air cell with solid directly below
          if (y > minY) {
            val below = vid(x, z, y - 1, minY)
            if (density[below] > 0.0) floorY = minOf(floorY, y)
          }

          // 6-neighbors
          if (x + 1 < 16) qt2 = pushIfAir(vid(x + 1, z, y, minY), q2, qt2)
          if (x - 1 >= 0) qt2 = pushIfAir(vid(x - 1, z, y, minY), q2, qt2)
          if (z + 1 < 16) qt2 = pushIfAir(vid(x, z + 1, y, minY), q2, qt2)
          if (z - 1 >= 0) qt2 = pushIfAir(vid(x, z - 1, y, minY), q2, qt2)
          if (y + 1 <= seaCap) qt2 = pushIfAir(vid(x, z, y + 1, minY), q2, qt2)
          if (y - 1 >= minY) qt2 = pushIfAir(vid(x, z, y - 1, minY), q2, qt2)
        }

        if (compSize < minCavitySize) continue
        if (maxCompY - minCompY < minCavityHeight) continue
        if (floorY == Int.MAX_VALUE) floorY = minCompY

        // ---- Noise anchor (snapped) ----
        val repWX = sumWX / compSize
        val repWZ = sumWZ / compSize
        val ax = snapToGrid(repWX, SNAP)
        val az = snapToGrid(repWZ, SNAP)

        // ---- Gate per-cavity so aquifers are rare ----
        val gate01 = (fillN.noise3D(ax, floorY, az) + 1.0) * 0.5
        if (gate01 < fillThreshold) continue

        // ---- Water table & depth from noises ----
        val level01 = (levelN.noise2D(ax, az) + 1.0) * 0.5
        val waterTableY = (waterTableBase + (level01 - 0.5) * 2.0 * waterTableAmp)
          .toInt()
          .coerceIn(minY + 8, minOf(sea - 2, maxY))

        val depth01 = (depthN.noise2D(ax, az) + 1.0) * 0.5
        val fillDepth = (minFillDepth + depth01 * (maxFillDepth - minFillDepth)).toInt()

        var aquiferTop = floorY + fillDepth

        // keep air gap and don't exceed water table
        aquiferTop = minOf(aquiferTop, maxCompY - headroom)
        aquiferTop = minOf(aquiferTop, waterTableY)

        if (aquiferTop <= floorY) continue

        // ---- Lava decision (deep-only, noise-driven) ----
        val lava01 = (lavaN.noise3D(ax, aquiferTop, az) + 1.0) * 0.5
        val useLava = aquiferTop <= lavaMaxY && lava01 > lavaThreshold
        val fluid = if (useLava) LAVA else WATER

        // ---- Fill component up to aquiferTop ----
        for (k in 0 until compSize) {
          val i = comp[k]
          val y = unpackY(i)
          if (y <= aquiferTop) {
            setBlock(chunkData, unpackX(i), y, unpackZ(i), fluid)
            //ctx.chunkContext.setBlock(unpackX(i), y, unpackZ(i), fluid)
          }
        }
      }
    }
  }*/
}
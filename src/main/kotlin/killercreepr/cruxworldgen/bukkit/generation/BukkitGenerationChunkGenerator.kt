package killercreepr.cruxworldgen.bukkit.generation

import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import killercreepr.crux.core.Crux
import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.terrain.TerrainSnapshot
import killercreepr.cruxworldgen.api.context.volumetric.VolBiomeBlendSample
import killercreepr.cruxworldgen.api.context.volumetric.VolumeEnv
import killercreepr.cruxworldgen.api.decor.DecorationPipeline
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.generation.GenerationPipeline
import killercreepr.cruxworldgen.api.noise.NoiseBank
import killercreepr.cruxworldgen.api.signal.SignalHandler
import killercreepr.cruxworldgen.api.structure.StructurePipeline
import killercreepr.cruxworldgen.bukkit.biome.BukkitBiome
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockData
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import killercreepr.cruxworldgen.bukkit.context.BukkitChunkContext
import killercreepr.cruxworldgen.bukkit.context.BukkitGenerateContext
import killercreepr.cruxworldgen.bukkit.context.BukkitMaterialContext
import killercreepr.cruxworldgen.bukkit.context.BukkitWorldContext
import killercreepr.cruxworldgen.bukkit.region.BukkitLimitedRegion
import killercreepr.cruxworldgen.core.context.SimpleTerrain2D
import killercreepr.cruxworldgen.core.context.SimpleTerrainSnapshot
import killercreepr.cruxworldgen.core.feature.FeaturePipeline
import killercreepr.cruxworldgen.core.noise.BaseNoiseKeys
import killercreepr.cruxworldgen.core.signal.SimpleSignalWriter
import org.bukkit.HeightMap
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.generator.*
import org.codehaus.plexus.util.FastMap
import java.util.*
import java.util.concurrent.ConcurrentHashMap

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
  val volumetricBiomeCellSize: Int = 4
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

  fun findSurfaceY(ctx: GenerateContext, biomeBlend: BiomeBlendSample, worldX: Int, worldZ: Int): Int {
    val minY = ctx.chunkContext.minHeight
    val maxY = ctx.chunkContext.maxHeight - 1

    val terrainDetailNoise = ctx.noise.get(BaseNoiseKeys.TerrainDetail)
    for (y in maxY downTo minY) {
      val terrainMacro =
        generation.blendedBiomeDensity(ctx, biomeBlend, worldX, y, worldZ, SignalHandler.DUMMY).finalDensity()

      val detail = terrainDetailNoise.noise3D(worldX, y, worldZ) * 3.0
      val terrainFinal = terrainMacro + detail
      if (terrainFinal > 0.0) return y
      val terrainDensity = generation.terrainDensityNoCaves(ctx, biomeBlend, worldX, y, worldZ, SignalHandler.DUMMY)
      if (terrainDensity > 0.0) return y
    }
    return minY
  }

  val cache = hashMapOf<Long, Cache>()

  data class Cache(
    val ctx: GenerateContext,
    val bufferX: Int,
    val bufferZ: Int,
    val minY: Int,
    val maxY: Int,
    val terrainSnapshot: TerrainSnapshot
  )

  fun chunkXFromWorld(worldX: Int, chunkWidth: Int = worldDetails.chunkWidth): Int =
    Math.floorDiv(worldX, chunkWidth)

  fun chunkZFromWorld(worldZ: Int, chunkDepth: Int = worldDetails.chunkDepth): Int =
    Math.floorDiv(worldZ, chunkDepth)

  override fun generateNoise(
    worldInfo: WorldInfo,
    random: Random,
    chunkX: Int,
    chunkZ: Int,
    chunkData: ChunkData
  ) {
    val chunkWidth = worldDetails.chunkWidth
    val chunkDepth = worldDetails.chunkDepth

    val baseWX = chunkX * chunkWidth
    val baseWZ = chunkZ * chunkDepth

    val bufferX = 32
    val bufferZ = 32

    val minWX = baseWX - bufferX
    val minWZ = baseWZ - bufferZ
    val width = chunkWidth + bufferX * 2
    val depth = chunkDepth + bufferZ * 2

    val ctx = BukkitGenerateContext(
      BukkitWorldContext(worldInfo),
      random, chunkX, chunkZ,
      BukkitChunkContext(chunkData.minHeight, chunkData.maxHeight, worldDetails.seaLevel, chunkWidth, chunkDepth),
      noise
    )
    val terrain2D = SimpleTerrain2D(generation, ctx, minWX, minWZ, width, depth)

    val minY = ctx.chunkContext.minHeight
    val maxY = ctx.chunkContext.maxHeight - 1
    val H = maxY - minY + 1

    fun vid(x: Int, z: Int, y: Int) = vid(x, z, y, minY)

    val density = DoubleArray(chunkWidth * chunkDepth * H)

    val primaryBiomeUse = arrayOfNulls<Biome>(chunkWidth * chunkDepth * H)

    val biomeCache = biomeProviderCache[chunkKey(chunkX, chunkZ)]
    val createBiomeCache = biomeCache == null

    val surfaceBlendCol = biomeCache?.columns?.surfaceBlend ?: arrayOfNulls(chunkWidth * chunkDepth)
    val surfaceYCol = biomeCache?.columns?.surfaceY ?: IntArray(chunkWidth * chunkDepth)
    fun columnIndex(blockX: Int, blockZ: Int) = blockZ * chunkWidth + blockX

    val cellSize = volumetricBiomeCellSize
    val cellsX = worldDetails.chunkWidth / cellSize
    val cellsZ = worldDetails.chunkDepth / cellSize
    val cellsY = ((maxY - minY + 1) + cellSize - 1) / cellSize


    val volBiomeBlendCell : Array<VolBiomeBlendSample?>? = if(createBiomeCache) arrayOfNulls(cellsX * cellsZ * cellsY) else null
    val biomeDominantCell = if(createBiomeCache) arrayOfNulls<Biome>(cellsX * cellsZ * cellsY) else null

    val terrainDetailNoise = ctx.noise.get(BaseNoiseKeys.TerrainDetail)
    for (localX in 0 until chunkWidth) {
      for (localZ in 0 until chunkDepth) {

        val signalWriter = SimpleSignalWriter(FastMap(32))
        val worldX = chunkX * chunkWidth + localX
        val worldZ = chunkZ * chunkDepth + localZ

        val cellX = cellXFromLocal(localX, cellSize).coerceIn(0, cellsX - 1)
        val cellZ = cellZFromLocal(localZ, cellSize).coerceIn(0, cellsZ - 1)

        val terrainIndex = terrain2D.idxUnsafe(worldX, worldZ)

        val biomeBlend: BiomeBlendSample
        if (biomeCache != null) {
          biomeBlend = biomeCache.columns.surfaceBlend[columnIndex(localX, localZ)]!!
        } else {
          val zone = generation.zones.sampleZone(ctx, worldX, worldZ)
          biomeBlend = zone.biomes.sampleBiomeBlend(ctx, worldX, worldZ)
          surfaceBlendCol[columnIndex(localX, localZ)] = biomeBlend
          surfaceYCol[columnIndex(localX, localZ)] = findSurfaceY(ctx, biomeBlend, worldX, worldZ)
        }

        val minY = ctx.chunkContext.minHeight
        val maxY = ctx.chunkContext.maxHeight - 1
        val height = maxY - minY + 1

        val airAbove = IntArray(height)
        val airBelow = IntArray(height)

        val col = DoubleArray(H)
        val surfaceY = surfaceYCol[columnIndex(localX, localZ)]
        terrain2D.surfaceY[terrainIndex] = surfaceY
        for (y in maxY downTo minY) {
          val terrainMacro =
            generation.blendedBiomeDensity(ctx, biomeBlend, worldX, y, worldZ, signalWriter).finalDensity()
          val detail = terrainDetailNoise.noise3D(worldX, y, worldZ) * 3.0
          val terrainFinal = terrainMacro + detail

          val env = VolumeEnv(
            surfaceY = surfaceY,
            depthBelowSurface = surfaceY - y,
            heightAboveSurface = y - surfaceY,
            terrainDensity = terrainFinal,
            seaLevel = ctx.chunkContext.seaLevel
          )
          val cellY = cellYFromWorld(y, cellSize, minY).coerceIn(0, cellsY - 1)
          val cellIndex = cellIndex(cellX, cellZ, cellY, cellSize, chunkWidth, chunkDepth)
          val volBlend = if(biomeCache != null){
            biomeCache.columns.volBiomeBlendCell[cellIndex]!!
          }else{
            var cachedValue = volBiomeBlendCell!![cellIndex]
            if(cachedValue == null){
              cachedValue = generation.volumetricBiomes.sample(ctx, worldX, y, worldZ, biomeBlend, env, signalWriter)
              volBiomeBlendCell[cellIndex] = cachedValue
            }
            cachedValue
          }

          val volStack = generation.blendedVolumetricDensity(ctx, volBlend, worldX, y, worldZ, env, signalWriter)
          val finalDensity = terrainFinal + volStack.add + volStack.base - volStack.carve

          val iy = y - minY
          col[iy] = finalDensity
          density[vid(localX, localZ, y)] = finalDensity
          if (finalDensity > 0.0 && y > terrain2D.skySurfaceY[terrainIndex]) {
            terrain2D.skySurfaceY[terrainIndex] = y
          }

          val volumetricContribution = volStack.base + volStack.add - volStack.carve
          val materialBiome =
            if (!volBlend.isEmpty() && volumetricContribution > 0.01) volBlend.dominant()
            else biomeBlend.primaryBiome()
          primaryBiomeUse[vid(localX, localZ, y)] = materialBiome

          if(createBiomeCache && biomeDominantCell!![cellIndex] == null){
            biomeDominantCell[cellIndex] = materialBiome
          }
        }

        var run = 0
        for (y in maxY downTo minY) {
          val iy = y - minY
          if (col[iy] <= 0.0) run++ else {
            airAbove[iy] = run; run = 0
          }
        }

        run = 0
        for (y in minY..maxY) {
          val iy = y - minY
          if (col[iy] <= 0.0) run++ else {
            airBelow[iy] = run; run = 0
          }
        }

        val sea = ctx.chunkContext.seaLevel
        val columnUnderwater = surfaceY < sea

        for (y in maxY downTo minY) {
          val iy = y - minY
          val d = col[iy]
          val isSolid = d > 0.0

          val depthBelowSurface = surfaceY - y

          val isUnderwater =
            columnUnderwater &&
              y <= sea &&
              airAbove[iy] >= 8 // "open water column" heuristic; prevents underwater-cave misflags

          val isOceanColumn = surfaceY < sea
          val depthFromSeaFloor = if (isOceanColumn) (surfaceY - y) else -1

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
            depthFromSeaFloor = depthFromSeaFloor,
            signalView = signalWriter
          )
          val mainBiome = primaryBiomeUse[vid(
            localX,
            localZ,
            y
          )]!!
          val chosenMaterial = mainBiome.materialProvider.chooseMaterial(materialContext)
          if (chosenMaterial != BlockData.NONE) {
            setBlock(chunkData, localX, y, localZ, chosenMaterial)
          }
        }
      }
    }

    fillFluids(chunkData, ctx, chunkX, chunkZ, density, terrain2D, minY, maxY)

    val terrain = SimpleTerrainSnapshot(terrain2D)

    val chunkKey = chunkKey(chunkX, chunkZ)
    cache[chunkKey] = Cache(
      ctx, bufferX, bufferZ,
      minY, maxY,
      terrain
    )

    if(createBiomeCache){
      Crux.logError("No biome cache for chunk! $chunkX,$chunkZ")
      biomeProviderCache.putIfAbsent(
        chunkKey,
        BiomeChunkCache(
          BiomeColumnCache(
            surfaceYCol, surfaceBlendCol, biomeDominantCell!!, volBiomeBlendCell!!
          )
        )
      )
    }
  }

  override fun getDefaultBiomeProvider(worldInfo: WorldInfo): BiomeProvider {
    return object : BiomeProvider() {
      override fun getBiome(
        worldInfo: WorldInfo,
        x: Int,
        y: Int,
        z: Int
      ): org.bukkit.block.Biome {
        val cx = chunkXFromWorld(x, worldDetails.chunkWidth)
        val cz = chunkZFromWorld(z, worldDetails.chunkDepth)

        val cache = getOrBuildBiomeChunkCache(worldInfo, cx, cz)
        val localX = Math.floorMod(x, worldDetails.chunkWidth)
        val localZ = Math.floorMod(z, worldDetails.chunkDepth)
        val columnIndex = idx16(localX, localZ)

        val minY = worldInfo.minHeight
        val maxY = worldInfo.maxHeight-1
        val clampedY = y.coerceIn(minY, maxY)

        val cellSize = volumetricBiomeCellSize
        val cellsX = worldDetails.chunkWidth / cellSize
        val cellsZ = worldDetails.chunkDepth / cellSize
        val cellsY = ((maxY - minY + 1) + cellSize - 1) / cellSize

        val cellX = cellXFromLocal(localX, cellSize).coerceIn(0, cellsX - 1)
        val cellZ = cellZFromLocal(localZ, cellSize).coerceIn(0, cellsZ - 1)
        val cellY = cellYFromWorld(clampedY, cellSize, minY).coerceIn(0, cellsY - 1)

        val main = cache.columns.biomeDominantCell[
          cellIndex(cellX, cellZ, cellY, cellSize, worldDetails.chunkWidth, worldDetails.chunkDepth)
        ] ?: cache.columns.surfaceBlend[columnIndex]!!.primaryBiome()

        return if (main is BukkitBiome) main.toBukkitBiome() else org.bukkit.block.Biome.PLAINS
      }

      override fun getBiomes(worldInfo: WorldInfo): List<org.bukkit.block.Biome?> = bukkitBiomes
    }
  }

  private fun blockIndex(localX: Int, localZ: Int, localY: Int, minY: Int): Int {
    return (localX and 15) + ((localZ and 15) shl 4) + ((localY - minY) shl 8)
  }

  private fun columnIndex(localX: Int, localZ: Int, chunkWidth: Int): Int {
    return localZ * chunkWidth + localX
  }

  private fun cellCountY(minY: Int, maxY: Int, cellSize: Int): Int {
    val height = (maxY - minY + 1)
    return (height + cellSize - 1) / cellSize // ceil
  }

  private fun cellIndex(
    cellX: Int,
    cellZ: Int,
    cellY: Int,
    cellsX: Int,
    cellsZ: Int
  ): Int {
    return (cellY * cellsZ + cellZ) * cellsX + cellX
  }

  private fun chunkLocalX(worldX: Int, chunkWidth: Int): Int = Math.floorMod(worldX, chunkWidth)
  private fun chunkLocalZ(worldZ: Int, chunkDepth: Int): Int = Math.floorMod(worldZ, chunkDepth)

  fun cellXFromWorld(worldX : Int, cellSize : Int) = Math.floorDiv(worldX, cellSize)
  fun cellZFromWorld(worldZ : Int, cellSize : Int) = Math.floorDiv(worldZ, cellSize)
  fun cellYFromWorld(worldY : Int, cellSize : Int, minY : Int) = Math.floorDiv(worldY - minY, cellSize)

  fun cellXFromLocal(localX : Int, cellSize : Int) = localX / cellSize
  fun cellZFromLocal(localZ : Int, cellSize : Int) = localZ / cellSize

  fun cellIndex(cellX: Int, cellZ: Int, cellY: Int, cellSize : Int, chunkWidth : Int, chunkDepth : Int): Int{
    val cellsX = chunkWidth / cellSize
    val cellsZ = chunkDepth / cellSize
    return (cellY * cellsZ + cellZ) * cellsX + cellX
  }

  fun getOrBuildBiomeChunkCache(worldInfo: WorldInfo, chunkX: Int, chunkZ: Int): BiomeChunkCache {
    val key = chunkKey(chunkX, chunkZ)
    biomeProviderCache[key]?.let { return it }

    val chunkWidth = worldDetails.chunkWidth
    val chunkDepth = worldDetails.chunkDepth

    val ctx = BukkitGenerateContext(
      BukkitWorldContext(worldInfo),
      Random(worldInfo.seed), chunkX, chunkZ,
      BukkitChunkContext(worldInfo.minHeight, worldInfo.maxHeight, worldDetails.seaLevel, chunkWidth, chunkDepth),
      noise
    )

    val minY = ctx.chunkContext.minHeight
    val maxY = ctx.chunkContext.maxHeight - 1
    val H = maxY - minY + 1

    val surfaceYArr = IntArray(chunkWidth * chunkDepth)
    val blendArr = arrayOfNulls<BiomeBlendSample>(chunkWidth * chunkDepth)

    for (lx in 0 until chunkWidth) for (lz in 0 until chunkDepth) {
      val wx = chunkX * chunkWidth + lx
      val wz = chunkZ * chunkDepth + lz
      val zone = generation.zones.sampleZone(ctx, wx, wz)
      val blend = zone.biomes.sampleBiomeBlend(ctx, wx, wz)
      blendArr[idx16(lx, lz)] = blend
      surfaceYArr[idx16(lx, lz)] = findSurfaceY(ctx, blend, wx, wz) // surface-only
    }

    val cellSize = volumetricBiomeCellSize
    val cellsX = chunkWidth / cellSize
    val cellsZ = chunkDepth / cellSize
    val cellsY = (H + cellSize - 1) / cellSize // ceil(H / cellSize)

    val biomeDominantCell = arrayOfNulls<Biome>(cellsX * cellsZ * cellsY)
    val volBiomeBlendCell = arrayOfNulls<VolBiomeBlendSample>(cellsX * cellsZ * cellsY)

    fun cellIndex(cellX: Int, cellZ: Int, cellY: Int): Int = (cellY * cellsZ + cellZ) * cellsX + cellX

    val dummySignals = SignalHandler.DUMMY

    for (cellX in 0 until cellsX) {
      for (cellZ in 0 until cellsZ) {
        val localBlockX = cellX * cellSize + (cellSize / 2)
        val localBlockZ = cellZ * cellSize + (cellSize / 2)

        val worldX = chunkX * chunkWidth + localBlockX
        val worldZ = chunkZ * chunkDepth + localBlockZ

        val surfaceBlend = blendArr[idx16(localBlockX, localBlockZ)]!!
        val surfaceY = surfaceYArr[idx16(localBlockX, localBlockZ)]

        for (cellY in 0 until cellsY) {
          val blockY = (minY + cellY * cellSize + (cellSize / 2)).coerceIn(minY, maxY)
          val result = generation.resolveMainBiome3D(
            ctx = ctx,
            signalWriter = dummySignals,
            worldX = worldX,
            y = blockY,
            worldZ = worldZ,
            surfaceY = surfaceY,
            surfaceBlend = surfaceBlend
          )

          val cellIndex = cellIndex(cellX, cellZ, cellY)
          biomeDominantCell[cellIndex] = result.first
          volBiomeBlendCell[cellIndex] = result.second
        }
      }
    }
    val built = BiomeChunkCache(BiomeColumnCache(surfaceYArr, blendArr, biomeDominantCell, volBiomeBlendCell))
    biomeProviderCache[key] = built
    return built
  }

  data class BiomeColumnCache(
    val surfaceY: IntArray,              // size 16*16
    val surfaceBlend: Array<BiomeBlendSample?>, // size 16*16 (or store primary biome id)
    //val volDominant: Array<Biome?>        // size 16*16 * stepsCount (optional)
    val biomeDominantCell : Array<Biome?>,
    val volBiomeBlendCell : Array<VolBiomeBlendSample?>
  )

  data class BiomeChunkCache(
    val columns: BiomeColumnCache
  )

  val biomeProviderCache = object : ConcurrentHashMap<Long, BiomeChunkCache>(256){}

  private fun chunkKey(cx: Int, cz: Int): Long = (cx.toLong() shl 32) xor (cz.toLong() and 0xffffffffL)

  private fun idx16(lx: Int, lz: Int) = (lz shl 4) or lx

  override fun getBaseHeight(
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
        val cache = cache.remove(chunkKey(chunkX, chunkZ)) ?: throw IllegalStateException("No cache for ${chunkX}x${chunkZ}")

        val region = BukkitLimitedRegion(
          cache.ctx, limitedRegion,
          limitedRegion.buffer, limitedRegion.buffer,
          cache.minY, cache.maxY,
          cache.terrainSnapshot
        )

        features.runForChunk(region, chunkX, chunkZ,
          { wx, wz ->
            val zone = generation.zones.sampleZone(region.ctx, wx, wz)
            zone.biomes.sampleBiomeBlend(region.ctx, wx, wz)
          },
          { wx, wy, wz ->
            getDominantVolumetricBiomeCached(worldInfo, wx, wy, wz)
          }
        )

        structures.runForChunk(region, chunkX, chunkZ)

        decorations.runAllPasses(region, chunkX, chunkZ) { wx, wz ->
          val zone = generation.zones.sampleZone(region.ctx, wx, wz)
          zone.biomes.sampleBiomeBlend(region.ctx, wx, wz)
        }
      }
    })
  }

  fun getDominantVolumetricBiomeCached(
    worldInfo: WorldInfo,
    worldX: Int,
    worldY: Int,
    worldZ: Int
  ): Biome {
    val chunkX = chunkXFromWorld(worldX, worldDetails.chunkWidth)
    val chunkZ = chunkZFromWorld(worldZ, worldDetails.chunkDepth)

    val cache = getOrBuildBiomeChunkCache(worldInfo, chunkX, chunkZ)

    val localX = Math.floorMod(worldX, worldDetails.chunkWidth)
    val localZ = Math.floorMod(worldZ, worldDetails.chunkDepth)

    val minY = worldInfo.minHeight
    val maxY = worldInfo.maxHeight - 1
    val clampedY = worldY.coerceIn(minY, maxY)

    val cellSize = volumetricBiomeCellSize
    val cellsX = worldDetails.chunkWidth / cellSize
    val cellsZ = worldDetails.chunkDepth / cellSize
    val height = maxY - minY + 1
    val cellsY = (height + cellSize - 1) / cellSize

    val cellX = (localX / cellSize).coerceIn(0, cellsX - 1)
    val cellZ = (localZ / cellSize).coerceIn(0, cellsZ - 1)
    val cellY = ((clampedY - minY) / cellSize).coerceIn(0, cellsY - 1)

    val index = (cellY * cellsZ + cellZ) * cellsX + cellX

    return cache.columns.biomeDominantCell[index]
      ?: cache.columns.surfaceBlend[idx16(localX, localZ)]!!.primaryBiome()
  }

  fun setBlock(chunkData: ChunkData, x: Int, y: Int, z: Int, block: BlockData) {
    (block as? BukkitBlockData) ?: error("$block is not a BukkitBlockData")
    block.setAt(chunkData, x, y, z)
  }

  fun vid(localX: Int, localZ: Int, y: Int, minY: Int) = (localX and 15) + ((localZ and 15) shl 4) + ((y - minY) shl 8)

  //todo make filling fluid better (some chunks don't get filled)
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
  }
}
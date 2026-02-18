package killercreepr.cruxworldgen.bukkit.generation

import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.terrain.TerrainSnapshot
import killercreepr.cruxworldgen.api.decor.DecorationPipeline
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.generation.GenerationPipeline
import killercreepr.cruxworldgen.api.noise.NoiseBank
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
import org.bukkit.block.Biome
import org.bukkit.generator.BiomeProvider
import org.bukkit.generator.BlockPopulator
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.LimitedRegion
import org.bukkit.generator.WorldInfo
import org.codehaus.plexus.util.FastMap
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
  val worldDetails : WorldDetails,
  val features : FeaturePipeline
) : ChunkGenerator() {
  val bukkitBiomes : List<org.bukkit.block.Biome>;
  init{
    val buildingBiomes = mutableListOf<org.bukkit.block.Biome>()
    generation.zones.zones.forEach { zone ->
      zone.biomes.biomes.forEach { biome ->
        if(biome !is BukkitBiome) return@forEach
        val bukkit = biome.toBukkitBiome()
        if(!buildingBiomes.contains(bukkit)) buildingBiomes.add(bukkit)
      }
    }
    bukkitBiomes = buildingBiomes
  }

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

  val cache = hashMapOf<Pair<Int, Int>, Cache>()

  data class Cache(
    val ctx: GenerateContext,
    val bufferX: Int,
    val bufferZ: Int,
    val minY: Int,
    val maxY: Int,
    val terrainSnapshot : TerrainSnapshot
  )

  override fun getBaseHeight(
    worldInfo: WorldInfo,
    random: Random,
    x: Int,
    z: Int,
    heightMap: HeightMap
  ): Int {
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

    val baseWX = chunkX * chunkWidth   // world X of localX=0
    val baseWZ = chunkZ * chunkDepth   // world Z of localZ=0

    val bufferX = 32  // blocks
    val bufferZ = 32  // blocks

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

    fun vid(x: Int, z: Int, y: Int) = (x and 15) + ((z and 15) shl 4) + ((y - minY) shl 8)

    val density = DoubleArray(16 * 16 * H)

    for (localX in 0 until chunkWidth) {
      for (localZ in 0 until chunkDepth) {

        val signalWriter = SimpleSignalWriter(FastMap(32))
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
        terrain2D.surfaceY[terrain2D.idxUnsafe(worldX, worldZ)] = surfaceY
        //surfaceYArr[localX + (localZ shl 4)] = surfaceY

        for (y in maxY downTo minY) {
          val terrainMacro = generation.blendedBiomeDensity(ctx, biomeBlend, worldX, y, worldZ).finalDensity()
          val detail = ctx.noise.get(BaseNoiseKeys.TerrainDetail).noise3D(worldX, y, worldZ) * 3.0
          val terrainFinal = terrainMacro + detail

          val caveCarve = generation.blendedBiomeCarve(ctx, biomeBlend, worldX, y, worldZ, surfaceY, terrainFinal, signalWriter)
          val caveAdd   = generation.blendedBiomeAdd(ctx, biomeBlend, worldX, y, worldZ, surfaceY, terrainFinal, signalWriter)

          val finalDensity = terrainFinal - caveCarve + caveAdd

          val iy = y - minY
          col[iy] = finalDensity
          density[vid(localX, localZ, y)] = finalDensity
          if(finalDensity > 0.0 && y > terrain2D.skySurfaceY[terrain2D.idxUnsafe(worldX, worldZ)]){
            terrain2D.skySurfaceY[terrain2D.idxUnsafe(worldX, worldZ)] = y
          }
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

          val chosenMaterial = biomeBlend.primaryBiome().materialProvider.chooseMaterial(materialContext)
          if (chosenMaterial != BlockData.NONE) {
            setBlock(chunkData,localX, y, localZ, chosenMaterial)
          }
        }
      }
    }

    fillFluids(chunkData,ctx, chunkX, chunkZ, density, terrain2D, minY, maxY)

    val terrain = SimpleTerrainSnapshot(terrain2D)

    cache[chunkX to chunkZ] = Cache(
      ctx,bufferX, bufferZ,
      minY, maxY,
      terrain
    )
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
        val cache = cache.remove(chunkX to chunkZ) ?: throw IllegalStateException("No cache for ${chunkX}x${chunkZ}")

        val region = BukkitLimitedRegion(
          cache.ctx, limitedRegion,
          limitedRegion.buffer, limitedRegion.buffer,
          cache.minY, cache.maxY,
          cache.terrainSnapshot
        )

        features.runForChunk(region, chunkX, chunkZ){ wx, wz ->
          val zone = generation.zones.sampleZone(region.ctx, wx, wz)
          zone.biomes.sampleBiomeBlend(region.ctx, wx, wz)
        }

        structures.runForChunk(region, chunkX, chunkZ)

        decorations.runAllPasses(region, chunkX, chunkZ) { wx, wz ->
          val zone = generation.zones.sampleZone(region.ctx, wx, wz)
          zone.biomes.sampleBiomeBlend(region.ctx, wx, wz)
        }
      }
    })
  }

  override fun getDefaultBiomeProvider(worldInfo: WorldInfo): BiomeProvider {
    return object : BiomeProvider(){
      override fun getBiome(
        worldInfo: WorldInfo,
        x: Int,
        y: Int,
        z: Int
      ): Biome {
        val chunkWidth = worldDetails.chunkWidth
        val chunkDepth = worldDetails.chunkDepth
        val chunkX = chunkXFromWorld(x, chunkWidth)
        val chunkZ = chunkZFromWorld(z, chunkDepth)

        val ctx = BukkitGenerateContext(
          BukkitWorldContext(worldInfo),
          Random(worldInfo.seed), chunkX, chunkZ,
          BukkitChunkContext(worldInfo.minHeight, worldInfo.maxHeight, worldDetails.seaLevel, chunkWidth, chunkDepth),
          noise
        )

        val zone = generation.zones.sampleZone(ctx, x, z)
        val biomeBlend = zone.biomes.sampleBiomeBlend(ctx, x, z)
        val primary = biomeBlend.primaryBiome()
        if(primary is BukkitBiome) return primary.toBukkitBiome()
        biomeBlend.weightedBiomes.forEach { biome ->
          val b = biome.biome
          if(b is BukkitBiome) return b.toBukkitBiome()
        }
        return Biome.PLAINS
      }

      override fun getBiomes(worldInfo: WorldInfo): List<Biome?> = bukkitBiomes
    }
  }

  fun setBlock(chunkData : ChunkData, x : Int, y : Int, z : Int, block: BlockData) {
    (block as? BukkitBlockData) ?: error("$block is not a BukkitBlockData")
    block.setAt(chunkData, x, y, z)
  }

  fun fillFluids(
    chunkData : ChunkData,
    ctx: BukkitGenerateContext,
    chunkX: Int, chunkZ: Int,
    density: DoubleArray,
    terrain2D: SimpleTerrain2D,
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
    val LAVA = BukkitBlockResolver.INSTANCE.resolve(Material.LAVA)
    val seaCap = minOf(sea, maxY)
    // 1) Fill surface water columns up to sea and seed BFS
    for (x in 0 until 16) for (z in 0 until 16) {
      val worldX = ctx.toWorldX(x)
      val worldZ = ctx.toWorldZ(z)

      val sY = terrain2D.surfaceY(worldX, worldZ)//surfaceY[x + (z shl 4)]
      val idx2D = terrain2D.idxUnsafe(worldX, worldZ)
      if (sY >= sea){
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
        val i = vid(x, z, y)
        if (density[i] <= 0.0 && !oceanConn[i]) {
          oceanConn[i] = true
          setBlock(chunkData, x,y,z, WATER)
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
        setBlock(chunkData, nx,ny,nz, WATER)
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
    val fillN  = ctx.noise.get(BaseNoiseKeys.AquiferFill3D)
    val lavaN  = ctx.noise.get(BaseNoiseKeys.AquiferLava3D)

    //val seaCap = minOf(sea, maxY)
    val baseWX = chunkX * ctx.chunkContext.width
    val baseWZ = chunkZ * ctx.chunkContext.depth

// Tunables (start here)
    val waterTableBase = sea - 18
    val waterTableAmp  = 14.0

    val headroom = 6            // keep air gap under ceiling
    val minFillDepth = 2        // puddles
    val maxFillDepth = 12       // small underground lakes

    val minCavitySize = 48      // makes aquifers rarer & avoids tiny drips
    val minCavityHeight = 4     // skip thin cracks

    val fillThreshold = 0.90    // higher = fewer aquifers (0.75..0.88 good range)

    val lavaMaxY = minY + 28
    val lavaThreshold = 0.74

// "snap" reduces cross-chunk disagreements because we sample noises at a stable anchor
    val SNAP = 64               // 32/64/96/128; higher = more coherence

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
        val start = vid(x0, z0, y0)
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
            val below = vid(x, z, y - 1)
            if (density[below] > 0.0) floorY = minOf(floorY, y)
          }

          // 6-neighbors
          if (x + 1 < 16) qt2 = pushIfAir(vid(x + 1, z, y), q2, qt2)
          if (x - 1 >= 0) qt2 = pushIfAir(vid(x - 1, z, y), q2, qt2)
          if (z + 1 < 16) qt2 = pushIfAir(vid(x, z + 1, y), q2, qt2)
          if (z - 1 >= 0) qt2 = pushIfAir(vid(x, z - 1, y), q2, qt2)
          if (y + 1 <= seaCap) qt2 = pushIfAir(vid(x, z, y + 1), q2, qt2)
          if (y - 1 >= minY) qt2 = pushIfAir(vid(x, z, y - 1), q2, qt2)
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
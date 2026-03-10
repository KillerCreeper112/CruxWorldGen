package killercreepr.cruxworldgen.core.generation.chunk

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.MaterialContext
import killercreepr.cruxworldgen.api.context.volumetric.VolBiomeBlendSample
import killercreepr.cruxworldgen.api.context.volumetric.VolumeEnv
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.generation.GenerationPipeline
import killercreepr.cruxworldgen.api.generation.chunk.ChunkSampler
import killercreepr.cruxworldgen.api.generation.chunk.SampledChunk
import killercreepr.cruxworldgen.api.noise.NoiseBank
import killercreepr.cruxworldgen.api.signal.SignalHandler
import killercreepr.cruxworldgen.api.signal.SignalWriter
import killercreepr.cruxworldgen.api.util.MathUtil.columnIndex
import killercreepr.cruxworldgen.api.util.MathUtil.cornerColumnIndex
import killercreepr.cruxworldgen.api.util.MathUtil.cornerIndex
import killercreepr.cruxworldgen.bukkit.context.BukkitChunkContext
import killercreepr.cruxworldgen.bukkit.context.BukkitGenerateContext
import killercreepr.cruxworldgen.bukkit.context.BukkitWorldContext
import killercreepr.cruxworldgen.bukkit.generation.WorldDetails
import killercreepr.cruxworldgen.core.context.SimpleCaveContext
import killercreepr.cruxworldgen.core.context.SimpleTerrain2D
import killercreepr.cruxworldgen.core.context.SimpleTerrain3D
import killercreepr.cruxworldgen.core.context.SimpleTerrainSnapshot
import killercreepr.cruxworldgen.core.noise.BaseNoiseKeys
import org.bukkit.generator.WorldInfo
import java.util.*

data class SimpleChunkSampler(
  override val generation: GenerationPipeline,
  override val noise: NoiseBank,
  override val worldDetails: WorldDetails,
  override val biomeCellSize: Int,

  val biomeCellCountX: Int = worldDetails.chunkWidth / biomeCellSize,
  val biomeCellCountZ: Int = worldDetails.chunkDepth / biomeCellSize,
) : ChunkSampler {
  fun sampleCellCorners(
    generateCtx: GenerateContext,
    chunkX: Int,
    chunkZ: Int,
    surfaceBlendByCornerColumn: Array<BiomeBlendSample?>,
    biomeCellCountX: Int,
    biomeCellCountZ: Int,
    biomeCellCountY: Int,
    minBlockY: Int,
    maxBlockY: Int,
    densityTerrainMacroByCorner: DoubleArray,
    densityCavesMacroByCorner: DoubleArray,
    volumetricBlendByCorner: Array<VolBiomeBlendSample?>
  ) {
    val chunkWidth = worldDetails.chunkWidth
    val chunkDepth = worldDetails.chunkDepth

    for (cellCornerX in 0..biomeCellCountX) {
      for (cellCornerZ in 0..biomeCellCountZ) {
        val localCornerX = cellCornerX * biomeCellSize
        val localCornerZ = cellCornerZ * biomeCellSize

        val sampleLocalX = localCornerX.coerceIn(0, chunkWidth - 1)
        val sampleLocalZ = localCornerZ.coerceIn(0, chunkDepth - 1)

        val worldX = chunkX * chunkWidth + localCornerX
        val worldZ = chunkZ * chunkDepth + localCornerZ

        //
        val zone = generation.zones.sampleZone(generateCtx, worldX, worldZ)
        val surfaceBlend = zone.biomes.sampleBiomeBlend(generateCtx, worldX, worldZ)
        val surfaceY = findSurfaceY(
          generateCtx, surfaceBlend,
          worldX, worldZ
        )

        val columnIndex = cornerColumnIndex(cellCornerX, cellCornerZ, biomeCellCountX)
        surfaceBlendByCornerColumn[columnIndex] = surfaceBlend
        //

        //val columnIndex = columnIndex(sampleLocalX, sampleLocalZ, chunkWidth)
        //val surfaceBlend = surfaceBiomeBlendByColumn[columnIndex]!!
        //val surfaceY = surfaceYByColumn[columnIndex]



        for (cellCornerY in 0..biomeCellCountY) {
          val worldY = (minBlockY + cellCornerY * biomeCellSize).coerceIn(minBlockY, maxBlockY)

          val cornerIndex = cornerIndex(cellCornerX, cellCornerZ, cellCornerY, biomeCellCountX, biomeCellCountZ)

          val terrainMacro = generation
            .blendedBiomeDensity(generateCtx, surfaceBlend, worldX, worldY, worldZ, SignalHandler.DUMMY)
            .finalDensity()

          val caveCtx = SimpleCaveContext(
            worldX, worldY, worldZ,
            surfaceY, surfaceY - worldY,
            terrainMacro, surfaceBlend.edgeContext,
            SignalHandler.DUMMY
          )

          val caveMacro = generation
            .blendedBiomeDensityCaves(generateCtx, surfaceBlend, worldX, worldY, worldZ, SignalHandler.DUMMY, caveCtx)
            .finalDensity()

          densityTerrainMacroByCorner[cornerIndex] = terrainMacro
          densityCavesMacroByCorner[cornerIndex] = caveMacro

          val env = VolumeEnv(
            surfaceY = surfaceY,
            depthBelowSurface = surfaceY - worldY,
            heightAboveSurface = worldY - surfaceY,
            terrainDensity = 0.0,
            seaLevel = generateCtx.chunkContext.seaLevel
          )

          val volBlend = generation.volumetricBiomes.sample(
            generateCtx, worldX, worldY, worldZ, surfaceBlend, env, SignalHandler.DUMMY
          )

          volumetricBlendByCorner[cornerIndex] = volBlend
        }
      }
    }
  }

  override fun sample(
    worldInfo: WorldInfo,
    random: Random,
    chunkX: Int,
    chunkZ: Int,
    signalWriter: SignalWriter
  ): SampledChunk {
    val chunkWidth = worldDetails.chunkWidth
    val chunkDepth = worldDetails.chunkDepth

    val generateCtx = BukkitGenerateContext(
      BukkitWorldContext(worldInfo),
      random,
      chunkX,
      chunkZ,
      BukkitChunkContext(
        worldInfo.minHeight,
        worldInfo.maxHeight,
        worldDetails.seaLevel,
        chunkWidth,
        chunkDepth
      ),
      noise
    )

    val minBlockY = generateCtx.chunkContext.minHeight
    val maxBlockY = generateCtx.chunkContext.maxHeight - 1
    val chunkBlockHeight = maxBlockY - minBlockY + 1

    val biomeCellCountY = (chunkBlockHeight + biomeCellSize - 1) / biomeCellSize

    val cornerArraySize = (biomeCellCountX + 1) * (biomeCellCountZ + 1) * (biomeCellCountY + 1)
    val cornerColumnArraySize = (biomeCellCountX + 1) * (biomeCellCountZ + 1)

    val densityTerrainMacroByCorner = DoubleArray(cornerArraySize)
    val densityCavesMacroByCorner = DoubleArray(cornerArraySize)

    val surfaceBlendByCornerColumn = arrayOfNulls<BiomeBlendSample?>(cornerColumnArraySize)
    val volumetricBlendByCorner = arrayOfNulls<VolBiomeBlendSample?>(cornerArraySize)

    val terrain2D = SimpleTerrain2D(
      generation, generateCtx,
      chunkX * chunkWidth - 32,
      chunkZ * chunkDepth - 32,
      chunkWidth + 64,
      chunkDepth + 64
    )
    val terrain3D = SimpleTerrain3D(
      generateCtx, BooleanArray(1)//todo
    )
    val terrainSnapshot = SimpleTerrainSnapshot(
      terrain2D, terrain3D
    )

    sampleCellCorners(
      generateCtx,
      chunkX, chunkZ, surfaceBlendByCornerColumn,
      biomeCellCountX, biomeCellCountZ,
      biomeCellCountY,
      minBlockY, maxBlockY,
      densityTerrainMacroByCorner,
      densityCavesMacroByCorner,
      volumetricBlendByCorner
    )

    return SimpleSampledChunk(
      generateCtx,
      densityTerrainMacroByCorner,
      densityCavesMacroByCorner,
      terrainSnapshot,
      surfaceBlendByCornerColumn,
      volumetricBlendByCorner
    )

    /*val chunkBlockWidth = worldDetails.chunkWidth
    val chunkBlockDepth = worldDetails.chunkDepth

    val generateCtx = BukkitGenerateContext(
      BukkitWorldContext(worldInfo),
      random,
      chunkX,
      chunkZ,
      BukkitChunkContext(
        worldInfo.minHeight,
        worldInfo.maxHeight,
        worldDetails.seaLevel,
        chunkBlockWidth,
        chunkBlockDepth
      ),
      noise
    )

    val minBlockY = generateCtx.chunkContext.minHeight
    val maxBlockY = generateCtx.chunkContext.maxHeight - 1
    val chunkBlockHeight = maxBlockY - minBlockY + 1

    val biomeCellSize = biomeCellSize
    val biomeCellCountX = chunkBlockWidth / biomeCellSize
    val biomeCellCountZ = chunkBlockDepth / biomeCellSize
    val biomeCellCountY = (chunkBlockHeight + biomeCellSize - 1) / biomeCellSize

    val cellArraySize = biomeCellCountX * biomeCellCountZ * biomeCellCountY

    val solidNoCavesByBlock = BitSet(chunkBlockWidth * chunkBlockDepth * chunkBlockHeight)
    val densityByCell = DoubleArray(cellArraySize)
    val materialBiomeByBlock = arrayOfNulls<Biome>(chunkBlockWidth * chunkBlockDepth * chunkBlockHeight)

    val surfaceBiomeBlendByColumn = arrayOfNulls<BiomeBlendSample>(chunkBlockWidth * chunkBlockDepth)
    val surfaceYByColumn = IntArray(chunkBlockWidth * chunkBlockDepth)

    val dominantBiomeByCell = arrayOfNulls<Biome>(cellArraySize)
    val volumetricBlendByCorner = arrayOfNulls<VolBiomeBlendSample>(
      (biomeCellCountX + 1) * (biomeCellCountZ + 1) * (biomeCellCountY + 1)
    )

    val caveAirByBlock = BooleanArray(chunkBlockWidth * chunkBlockDepth * chunkBlockHeight)
    val terrainField2D = SimpleTerrain2D(
      generation,
      generateCtx,
      chunkX * chunkBlockWidth - 32,
      chunkZ * chunkBlockDepth - 32,
      chunkBlockWidth + 64,
      chunkBlockDepth + 64
    )

    val terrainField3D = SimpleTerrain3D(
      generateCtx,
      caveAirByBlock
    )

    val terrainSnapshot = SimpleTerrainSnapshot(
      terrainField2D,
      terrainField3D
    )

    *//*sampleSurfaceColumns(
      generateCtx,
      chunkX,
      chunkZ,
      surfaceBiomeBlendByColumn,
      surfaceYByColumn,
      terrainField2D
    )*//*

    sampleVolumetricCorners(
      generateCtx,
      chunkX,
      chunkZ,
      surfaceBiomeBlendByColumn,
      surfaceYByColumn,
      volumetricBlendByCorner,
      biomeCellCountX,
      biomeCellCountZ,
      biomeCellCountY,
      biomeCellSize,
      minBlockY,
      maxBlockY
    )

    val terrainMacroByCell = DoubleArray(cellArraySize)
    val caveMacroByCell = DoubleArray(cellArraySize)

    sampleBlockData(
      generateCtx,
      chunkX,
      chunkZ,
      surfaceBiomeBlendByColumn,
      surfaceYByColumn,
      volumetricBlendByCorner,
      dominantBiomeByCell,
      materialBiomeByBlock,
      densityByCell,
      terrainField2D,
      terrainField3D,
      biomeCellCountX,
      biomeCellCountZ,
      biomeCellCountY,
      biomeCellSize,
      minBlockY,
      maxBlockY,
      signalWriter,
      solidNoCavesByBlock,
      terrainMacroByCell = terrainMacroByCell,
      caveMacroByCell = caveMacroByCell,
    )
    return SimpleSampledChunk(
      ctx = generateCtx,
      density = densityByCell,
      surfaceY = surfaceYByColumn,
      surfaceBlend = surfaceBiomeBlendByColumn,
      dominantBiomeByBlock = materialBiomeByBlock,
      terrainSnapshot = terrainSnapshot,
      volBiomeCorners = volumetricBlendByCorner,
      solidNoCavesByBlock = solidNoCavesByBlock,
      terrainMacroByCell = terrainMacroByCell,
      caveMacroByCell = caveMacroByCell,
    )*/
  }

  fun findSurfaceY(
    ctx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    worldZ: Int
  ): Int {
    val minY = ctx.chunkContext.minHeight
    val maxY = ctx.chunkContext.maxHeight - 1
    val terrainDetailNoise = ctx.noise.get(BaseNoiseKeys.TerrainDetail)

    fun isSolid(y: Int): Boolean {
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
      if (isSolid(y)) {
        val refineTop = minOf(maxY, y + step - 1)
        for (yy in refineTop downTo (y + 1)) {
          if (isSolid(yy)) return yy
        }
        return y
      }
      y -= step
    }

    return minY
  }

  fun sampleSurfaceColumns(
    generateCtx: GenerateContext,
    chunkX: Int,
    chunkZ: Int,
    surfaceBiomeBlendByColumn: Array<BiomeBlendSample?>,
    surfaceYByColumn: IntArray,
    terrainField2D: SimpleTerrain2D
  ) {
    val chunkWidth = worldDetails.chunkWidth
    val chunkDepth = worldDetails.chunkDepth

    for (localX in 0 until chunkWidth) {
      for (localZ in 0 until chunkDepth) {
        val worldX = chunkX * chunkWidth + localX
        val worldZ = chunkZ * chunkDepth + localZ

        val zone = generation.zones.sampleZone(generateCtx, worldX, worldZ)
        val surfaceBlend = zone.biomes.sampleBiomeBlend(generateCtx, worldX, worldZ)
        val surfaceY = findSurfaceY(
          generateCtx, surfaceBlend,
          worldX, worldZ
        )

        val columnIndex = columnIndex(localX, localZ, chunkWidth)
        surfaceBiomeBlendByColumn[columnIndex] = surfaceBlend
        surfaceYByColumn[columnIndex] = surfaceY

        val terrainIndex = terrainField2D.idxUnsafe(worldX, worldZ)
        terrainField2D.surfaceY[terrainIndex] = surfaceY
      }
    }
  }

  fun sampleVolumetricCorners(
    generateCtx: GenerateContext,
    chunkX: Int,
    chunkZ: Int,
    surfaceBiomeBlendByColumn: Array<BiomeBlendSample?>,
    surfaceYByColumn: IntArray,
    volumetricBlendByCorner: Array<VolBiomeBlendSample?>,
    biomeCellCountX: Int,
    biomeCellCountZ: Int,
    biomeCellCountY: Int,
    biomeCellSize: Int,
    minBlockY: Int,
    maxBlockY: Int
  ) {
    val chunkWidth = worldDetails.chunkWidth
    val chunkDepth = worldDetails.chunkDepth

    for (cellCornerX in 0..biomeCellCountX) {
      for (cellCornerZ in 0..biomeCellCountZ) {
        val localCornerX = cellCornerX * biomeCellSize
        val localCornerZ = cellCornerZ * biomeCellSize

        val sampleLocalX = localCornerX.coerceIn(0, chunkWidth - 1)
        val sampleLocalZ = localCornerZ.coerceIn(0, chunkDepth - 1)

        val worldX = chunkX * chunkWidth + localCornerX
        val worldZ = chunkZ * chunkDepth + localCornerZ

        //
        val zone = generation.zones.sampleZone(generateCtx, worldX, worldZ)
        val surfaceBlend = zone.biomes.sampleBiomeBlend(generateCtx, worldX, worldZ)
        val surfaceY = findSurfaceY(
          generateCtx, surfaceBlend,
          worldX, worldZ
        )

        val columnIndex = columnIndex(sampleLocalX, sampleLocalZ, chunkWidth)
        surfaceBiomeBlendByColumn[columnIndex] = surfaceBlend
        surfaceYByColumn[columnIndex] = surfaceY
        //

        //val columnIndex = columnIndex(sampleLocalX, sampleLocalZ, chunkWidth)
        //val surfaceBlend = surfaceBiomeBlendByColumn[columnIndex]!!
        //val surfaceY = surfaceYByColumn[columnIndex]

        for (cellCornerY in 0..biomeCellCountY) {
          val worldY = (minBlockY + cellCornerY * biomeCellSize).coerceIn(minBlockY, maxBlockY)

          val env = VolumeEnv(
            surfaceY = surfaceY,
            depthBelowSurface = surfaceY - worldY,
            heightAboveSurface = worldY - surfaceY,
            terrainDensity = 0.0,
            seaLevel = generateCtx.chunkContext.seaLevel
          )

          val volBlend = generation.volumetricBiomes.sample(
            generateCtx, worldX, worldY, worldZ, surfaceBlend, env, SignalHandler.DUMMY
          )

          volumetricBlendByCorner[
            cornerIndex(cellCornerX, cellCornerZ, cellCornerY, biomeCellCountX, biomeCellCountZ)
          ] = volBlend
        }
      }
    }
  }

  fun sampleBlockData(
    generateCtx: GenerateContext,
    chunkX: Int,
    chunkZ: Int,
    surfaceBiomeBlendByColumn: Array<BiomeBlendSample?>,
    surfaceYByColumn: IntArray,
    volumetricBlendByCorner: Array<VolBiomeBlendSample?>,
    dominantBiomeByCell: Array<Biome?>,
    materialBiomeByBlock: Array<Biome?>,
    densityByBlock: DoubleArray,
    terrainField2D: SimpleTerrain2D,
    terrainField3D: SimpleTerrain3D,
    biomeCellCountX: Int,
    biomeCellCountZ: Int,
    biomeCellCountY: Int,
    biomeCellSize: Int,
    minBlockY: Int,
    maxBlockY: Int,
    signalWriter: SignalWriter,
    solidNoCavesByBlock : BitSet,
    terrainMacroByCell: DoubleArray,
    caveMacroByCell: DoubleArray
  ) {
    val chunkWidth = worldDetails.chunkWidth
    val chunkDepth = worldDetails.chunkDepth

    val terrainDetailNoise = generateCtx.noise.get(BaseNoiseKeys.TerrainDetail)

    fun cornerIndexLocal(cellX: Int, cellZ: Int, cellY: Int): Int {
      return (cellY * (biomeCellCountZ + 1) + cellZ) * (biomeCellCountX + 1) + cellX
    }

    for (cellCornerX in 0..biomeCellCountX) {
      for (cellCornerZ in 0..biomeCellCountZ) {
        val localCornerX = cellCornerX * biomeCellSize
        val localCornerZ = cellCornerZ * biomeCellSize

        val worldX = chunkX * chunkWidth + localCornerX
        val worldZ = chunkZ * chunkDepth + localCornerZ

        val sampleLocalX = localCornerX.coerceIn(0, chunkWidth - 1)
        val sampleLocalZ = localCornerZ.coerceIn(0, chunkDepth - 1)

        val columnIdx = columnIndex(sampleLocalX, sampleLocalZ, chunkWidth)
        val surfaceBlend = surfaceBiomeBlendByColumn[columnIdx]!!
        val surfaceY = surfaceYByColumn[columnIdx]

        for (cellCornerY in 0..biomeCellCountY) {
          val worldY = (minBlockY + cellCornerY * biomeCellSize).coerceIn(minBlockY, maxBlockY)

          val terrainMacro = generation
            .blendedBiomeDensity(generateCtx, surfaceBlend, worldX, worldY, worldZ, SignalHandler.DUMMY)
            .finalDensity()

          val caveCtx = SimpleCaveContext(
            worldX, worldY, worldZ,
            surfaceY, surfaceY - worldY,
            terrainMacro, surfaceBlend.edgeContext,
            SignalHandler.DUMMY
          )

          val caveMacro = generation
            .blendedBiomeDensityCaves(generateCtx, surfaceBlend, worldX, worldY, worldZ, SignalHandler.DUMMY, caveCtx)
            .finalDensity()

          val idx = cornerIndex(cellCornerX, cellCornerZ, cellCornerY, biomeCellCountX, biomeCellCountZ)
          terrainMacroByCell[idx] = terrainMacro
          caveMacroByCell[idx] = caveMacro
        }
      }
    }

    /*for (localX in 0 until chunkWidth) {
      for (localZ in 0 until chunkDepth) {
        val worldX = chunkX * chunkWidth + localX
        val worldZ = chunkZ * chunkDepth + localZ

        val columnIdx = columnIndex(localX, localZ, chunkWidth)
        val surfaceBlend = surfaceBiomeBlendByColumn[columnIdx]!!
        val surfaceY = surfaceYByColumn[columnIdx]

        val terrainIndex = terrainField2D.idxUnsafe(worldX, worldZ)

        val cellX = (localX / biomeCellSize).coerceIn(0, biomeCellCountX - 1)
        val cellZ = (localZ / biomeCellSize).coerceIn(0, biomeCellCountZ - 1)

        val localCellOriginX = cellX * biomeCellSize
        val localCellOriginZ = cellZ * biomeCellSize

        val tx = (((localX - localCellOriginX).toDouble() + 0.5) / biomeCellSize.toDouble()).coerceIn(0.0, 1.0)
        val tz = (((localZ - localCellOriginZ).toDouble() + 0.5) / biomeCellSize.toDouble()).coerceIn(0.0, 1.0)

        for (blockY in maxBlockY downTo minBlockY) {
          val terrainMacro = generation
            .blendedBiomeDensity(generateCtx, surfaceBlend, worldX, blockY, worldZ, signalWriter)
            .finalDensity()

          val caveCtx = SimpleCaveContext(
            worldX, blockY, worldZ, surfaceY, surfaceY - blockY,
            terrainMacro, surfaceBlend.edgeContext,
            signalWriter
          )

          val caveMacro = generation.blendedBiomeDensityCaves(
            generateCtx, surfaceBlend, worldX, blockY, worldZ, signalWriter, caveCtx
          ).finalDensity()

          val detail = terrainDetailNoise.noise3D(worldX, blockY, worldZ) * 3.0
          val densityBeforeCaves = terrainMacro + detail

          if(densityBeforeCaves > 0) solidNoCavesByBlock[blockIndex(localX, localZ, blockY, minBlockY, chunkWidth, chunkDepth)] = true

          val terrainFinal = densityBeforeCaves + caveMacro

          val env = VolumeEnv(
            surfaceY = surfaceY,
            depthBelowSurface = surfaceY - blockY,
            heightAboveSurface = blockY - surfaceY,
            terrainDensity = terrainFinal,
            seaLevel = generateCtx.chunkContext.seaLevel
          )

          val cellY = cellYFromWorld(blockY, biomeCellSize, minBlockY).coerceIn(0, biomeCellCountY - 1)
          val localCellOriginY = minBlockY + cellY * biomeCellSize

          val ty = (((blockY - localCellOriginY).toDouble() + 0.5) / biomeCellSize.toDouble()).coerceIn(0.0, 1.0)

          val c000 = volumetricBlendByCorner[cornerIndexLocal(cellX,     cellZ,     cellY    )]!!
          val c100 = volumetricBlendByCorner[cornerIndexLocal(cellX + 1, cellZ,     cellY    )]!!
          val c010 = volumetricBlendByCorner[cornerIndexLocal(cellX,     cellZ + 1, cellY    )]!!
          val c110 = volumetricBlendByCorner[cornerIndexLocal(cellX + 1, cellZ + 1, cellY    )]!!
          val c001 = volumetricBlendByCorner[cornerIndexLocal(cellX,     cellZ,     cellY + 1)]!!
          val c101 = volumetricBlendByCorner[cornerIndexLocal(cellX + 1, cellZ,     cellY + 1)]!!
          val c011 = volumetricBlendByCorner[cornerIndexLocal(cellX,     cellZ + 1, cellY + 1)]!!
          val c111 = volumetricBlendByCorner[cornerIndexLocal(cellX + 1, cellZ + 1, cellY + 1)]!!

          val volBlend = VolBiomeBlendSample.interpolateVolBlend(
            c000, c100, c010, c110, c001, c101, c011, c111, tx, ty, tz
          )

          val volStack = generation.blendedVolumetricDensity(
            generateCtx, volBlend, worldX, blockY, worldZ, env, signalWriter
          )

          val finalDensity = terrainFinal + volStack.add + volStack.base - volStack.carve

          if(densityBeforeCaves > 0.0 && finalDensity <= 0.0){
            terrainField3D.caveAirByBlock[blockIndex(localX, localZ, blockY, minBlockY, chunkWidth, chunkDepth)] = true
          }

          val blockIdx = blockIndex(localX, localZ, blockY, minBlockY, chunkWidth, chunkDepth)
          densityByBlock[blockIdx] = finalDensity

          val volumetricContribution = volStack.base + volStack.add - volStack.carve
          val materialBiome =
            if (!volBlend.isEmpty() && volumetricContribution > 0.01) volBlend.dominant()
            else surfaceBlend.primaryBiome()

          materialBiomeByBlock[blockIdx] = materialBiome

          val cellIdx = cellIndex(cellX, cellZ, cellY, biomeCellCountX, biomeCellCountZ)
          if (dominantBiomeByCell[cellIdx] == null) {
            dominantBiomeByCell[cellIdx] = materialBiome
          }

          if (finalDensity > 0.0 && blockY > terrainField2D.skySurfaceY[terrainIndex]) {
            terrainField2D.skySurfaceY[terrainIndex] = blockY
          }
        }
      }
    }*/
  }
}
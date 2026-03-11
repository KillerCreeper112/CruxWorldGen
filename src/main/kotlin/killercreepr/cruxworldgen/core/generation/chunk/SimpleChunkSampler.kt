package killercreepr.cruxworldgen.core.generation.chunk

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.volumetric.VolBiomeBlendSample
import killercreepr.cruxworldgen.api.context.volumetric.VolumeEnv
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.generation.GenerationPipeline
import killercreepr.cruxworldgen.api.generation.chunk.ChunkSampler
import killercreepr.cruxworldgen.api.generation.chunk.SampledChunk
import killercreepr.cruxworldgen.api.noise.NoiseBank
import killercreepr.cruxworldgen.api.signal.SignalHandler
import killercreepr.cruxworldgen.api.signal.SignalWriter
import killercreepr.cruxworldgen.api.util.Curve
import killercreepr.cruxworldgen.api.util.MathUtil.blockIndex
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
import net.minecraft.world.level.levelgen.NoiseChunk
import org.bukkit.generator.WorldInfo
import java.util.*

data class SimpleChunkSampler(
  override val generation: GenerationPipeline,
  override val noise: NoiseBank,
  override val worldDetails: WorldDetails,
  override val biomeCellSize: Int,
  override val mediumCellSize: Int,

  val biomeCellCountX: Int = worldDetails.chunkWidth / biomeCellSize,
  val biomeCellCountZ: Int = worldDetails.chunkDepth / biomeCellSize,

  val mediumCellCountX: Int = worldDetails.chunkWidth / mediumCellSize,
  val mediumCellCountZ: Int = worldDetails.chunkDepth / mediumCellSize,
) : ChunkSampler {
  fun sampleMediumCellCorners(
    generateCtx: GenerateContext,
    chunkX: Int,
    chunkZ: Int,
    surfaceBlendByCornerColumn: Array<BiomeBlendSample?>,
    cellCountX: Int,
    cellCountZ: Int,
    cellCountY: Int,
    cellSize: Int,
    minBlockY: Int,
    maxBlockY: Int,
    densityTerrainMacroByCorner: DoubleArray,
    densityCavesMacroByCorner: DoubleArray,
    chunkBlockHeight: Int,
    caveCache: Array<Any?>
  ) {
    val chunkWidth = worldDetails.chunkWidth
    val chunkDepth = worldDetails.chunkDepth

    for (cellCornerX in 0..cellCountX) {
      for (cellCornerZ in 0..cellCountZ) {
        val localCornerX = cellCornerX * cellSize
        val localCornerZ = cellCornerZ * cellSize

        val worldX = chunkX * chunkWidth + localCornerX
        val worldZ = chunkZ * chunkDepth + localCornerZ

        val surfaceBlend = run {
          //val biomeCellX = (localCornerX / biomeCellSize).coerceIn(0, biomeCellCountX - 1)
          //val biomeCellZ = (localCornerZ / biomeCellSize).coerceIn(0, biomeCellCountZ - 1)
          //val surfaceCornerColumnIndex = cornerColumnIndex(biomeCellX, biomeCellZ, biomeCellCountX)

          //todo bad bandaid fix
          val surfaceCornerColumnIndex = cornerColumnIndex(cellCornerX, cellCornerZ, biomeCellCountX)
          surfaceBlendByCornerColumn[surfaceCornerColumnIndex]!!
        }

        for (cellCornerY in 0..cellCountY) {
          val worldY = (minBlockY + cellCornerY * cellSize).coerceIn(minBlockY, maxBlockY)

          val cornerIndex = cornerIndex(cellCornerX, cellCornerZ, cellCornerY, cellCountX, cellCountZ)

          val biomeCellCountY = (chunkBlockHeight + biomeCellSize - 1) / biomeCellSize
          val terrainMacro = interpolatedCornerDensityAt(
            localX = localCornerX,
            blockY = worldY,
            localZ = localCornerZ,
            minBlockY = minBlockY,
            cellSize = biomeCellSize,
            cellCountX = biomeCellCountX,
            cellCountZ = biomeCellCountZ,
            cellCountY = biomeCellCountY,
            densityByCorner = densityTerrainMacroByCorner
          )
          /*val caveCtx = SimpleCaveContext(
            worldX, worldY, worldZ,
            0, 0,
            terrainMacro, surfaceBlend.edgeContext,
            SignalHandler.DUMMY
          )*/

          caveCache[cornerIndex] = generation.blendedBiomeDensityCavesCache(
            generateCtx, surfaceBlend, worldX, worldY, worldZ,
            SignalHandler.DUMMY, terrainMacro
          )

          /*val caveMacro = generation
            .blendedBiomeDensityCaves(generateCtx, surfaceBlend, worldX, worldY, worldZ, SignalHandler.DUMMY, caveCtx)
            .finalDensity()
          densityCavesMacroByCorner[cornerIndex] = caveMacro*/
        }
      }
    }
  }

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
        /*val surfaceY = findSurfaceY(
          generateCtx, surfaceBlend,
          worldX, worldZ
        )*/

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

          /*val caveCtx = SimpleCaveContext(
            worldX, worldY, worldZ,
            0, 0,
            terrainMacro, surfaceBlend.edgeContext,
            SignalHandler.DUMMY
          )*/

          /*val caveMacro = generation
            .blendedBiomeDensityCaves(generateCtx, surfaceBlend, worldX, worldY, worldZ, SignalHandler.DUMMY, caveCtx)
            .finalDensity()*/

          densityTerrainMacroByCorner[cornerIndex] = terrainMacro
          //densityCavesMacroByCorner[cornerIndex] = caveMacro

          val env = VolumeEnv(
            surfaceY = 0,//surfaceY,
            depthBelowSurface = 0,//surfaceY - worldY,
            heightAboveSurface = 0,//worldY - surfaceY,
            terrainDensity = terrainMacro,
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

  fun interpolatedCornerDensityAt(
    localX: Int,
    blockY: Int,
    localZ: Int,
    minBlockY: Int,
    cellSize: Int,
    cellCountX: Int,
    cellCountZ: Int,
    cellCountY: Int,
    densityByCorner: DoubleArray,
    smooth: Boolean = false,
  ): Double {
    val cellX = (localX / cellSize).coerceIn(0, cellCountX - 1)
    val cellZ = (localZ / cellSize).coerceIn(0, cellCountZ - 1)
    val cellY = ((blockY - minBlockY) / cellSize).coerceIn(0, cellCountY - 1)

    val localCellOriginX = cellX * cellSize
    val localCellOriginZ = cellZ * cellSize
    val localCellOriginY = minBlockY + cellY * cellSize

    var tx = (((localX - localCellOriginX).toDouble() + 0.5) / cellSize.toDouble()).coerceIn(0.0, 1.0)
    var tz = (((localZ - localCellOriginZ).toDouble() + 0.5) / cellSize.toDouble()).coerceIn(0.0, 1.0)
    var ty = (((blockY - localCellOriginY).toDouble() + 0.5) / cellSize.toDouble()).coerceIn(0.0, 1.0)

    if (smooth) {
      tx = Curve.smoothstep01(tx)
      tz = Curve.smoothstep01(tz)
      ty = Curve.smoothstep01(ty)
    }

    val c000 = cornerIndex(cellX, cellZ, cellY, cellCountX, cellCountZ)
    val c100 = cornerIndex(cellX + 1, cellZ, cellY, cellCountX, cellCountZ)
    val c010 = cornerIndex(cellX, cellZ + 1, cellY, cellCountX, cellCountZ)
    val c110 = cornerIndex(cellX + 1, cellZ + 1, cellY, cellCountX, cellCountZ)
    val c001 = cornerIndex(cellX, cellZ, cellY + 1, cellCountX, cellCountZ)
    val c101 = cornerIndex(cellX + 1, cellZ, cellY + 1, cellCountX, cellCountZ)
    val c011 = cornerIndex(cellX, cellZ + 1, cellY + 1, cellCountX, cellCountZ)
    val c111 = cornerIndex(cellX + 1, cellZ + 1, cellY + 1, cellCountX, cellCountZ)

    return Curve.trilerp(
      densityByCorner[c000],
      densityByCorner[c100],
      densityByCorner[c010],
      densityByCorner[c110],
      densityByCorner[c001],
      densityByCorner[c101],
      densityByCorner[c011],
      densityByCorner[c111],
      tx, ty, tz
    )
  }

  fun interpolate(
    cornerIndex: Int,
    cellX: Int,
    cellY: Int,
    cellZ: Int,

    cellCountX: Int,
    cellCountZ: Int,
    densityByCorner: DoubleArray,
    tx: Double,
    ty: Double,
    tz: Double
  ): Double {
    val c000 = cornerIndex
    val c100 = cornerIndex(cellX + 1, cellZ, cellY, cellCountX, cellCountZ)
    val c010 = cornerIndex(cellX, cellZ + 1, cellY, cellCountX, cellCountZ)
    val c110 = cornerIndex(cellX + 1, cellZ + 1, cellY, cellCountX, cellCountZ)

    val c001 = cornerIndex(cellX, cellZ, cellY + 1, cellCountX, cellCountZ)
    val c101 = cornerIndex(cellX + 1, cellZ, cellY + 1, cellCountX, cellCountZ)
    val c011 = cornerIndex(cellX, cellZ + 1, cellY + 1, cellCountX, cellCountZ)
    val c111 = cornerIndex(cellX + 1, cellZ + 1, cellY + 1, cellCountX, cellCountZ)

    return Curve.trilerp(
      densityByCorner[c000],
      densityByCorner[c100],
      densityByCorner[c010],
      densityByCorner[c110],
      densityByCorner[c001],
      densityByCorner[c101],
      densityByCorner[c011],
      densityByCorner[c111],
      tx, ty, tz
    )
  }

  fun sampleDensity(
    ctx: GenerateContext,
    chunkX: Int,
    chunkZ: Int,
    surfaceBlendByCornerColumn: Array<BiomeBlendSample?>,
    biomeCellCountX: Int,
    biomeCellCountZ: Int,
    biomeCellCountY: Int,
    mediumCellCountX: Int,
    mediumCellCountZ: Int,
    mediumCellCountY: Int,
    minY: Int,
    maxY: Int,
    densityTerrainMacroByCorner: DoubleArray,
    densityCavesMacroByCorner: DoubleArray,
    volumetricBlendByCorner: Array<VolBiomeBlendSample?>,
    densityByBlock: DoubleArray,
    primaryBiomeByBlock: Array<Biome?>,
    surfaceYByBlockColumn: IntArray,
    solidNoCavesByBlock: BitSet,
    terrain3D: SimpleTerrain3D,
    signalWriter: SignalWriter,
    chunkBlockHeight: Int,
    caveCache: Array<Any?>
  ) {
    val terrainDetailNoise = ctx.noise.get(BaseNoiseKeys.TerrainDetail)
    val caveDetailNoise = ctx.noise.get(BaseNoiseKeys.CaveDetail)
    val chunkWidth = worldDetails.chunkWidth
    val chunkDepth = worldDetails.chunkDepth
    val height = maxY - minY + 1

    val mediumCellCountY = (chunkBlockHeight + mediumCellSize - 1) / mediumCellSize

    val mediumCornerArraySize = (mediumCellCountX + 1) * (mediumCellCountZ + 1) * (mediumCellCountY + 1)

    //val blockCache = arrayOfNulls<Any>(mediumCornerArraySize)

    for (localX in 0 until chunkWidth) {
      for (localZ in 0 until chunkDepth) {
        val worldX = chunkX * chunkWidth + localX
        val worldZ = chunkZ * chunkDepth + localZ

        val columnIndex = columnIndex(localX, localZ, chunkWidth)
        //val surfaceY = surfaceY[columnIndex]

        val airAbove = IntArray(height)
        val airBelow = IntArray(height)

        val caveAirAbove = IntArray(height)
        val caveAirBelow = IntArray(height)

        /*computeAirRuns(
          density, localX, localZ, minY, maxY, chunkWidth, chunkDepth, airAbove, airBelow,
          caveAirAbove, caveAirBelow, terrain3D
        )*/

        val seaLevel = ctx.chunkContext.seaLevel
        //val columnUnderwater = surfaceY < seaLevel

        val cellX = (localX / biomeCellSize).coerceIn(0, biomeCellCountX - 1)
        val cellZ = (localZ / biomeCellSize).coerceIn(0, biomeCellCountZ - 1)

        val mediumCellX = (localX / mediumCellSize).coerceIn(0, mediumCellCountX - 1)
        val mediumCellZ = (localZ / mediumCellSize).coerceIn(0, mediumCellCountZ - 1)

        val localCellOriginX = cellX * biomeCellSize
        val localCellOriginZ = cellZ * biomeCellSize

        val localMediumCellOriginX = mediumCellX * mediumCellSize
        val localMediumCellOriginZ = mediumCellZ * mediumCellSize

        //val baseSurfaceYColumn = IntArray(height)
        val baseTerrainDensityColumn = DoubleArray(height)

        var baseSurfaceY = minY
        var foundBaseSurfaceY = false
        for (blockY in maxY downTo minY) {
          val iy = blockY - minY

          val cellY = ((blockY - minY) / biomeCellSize).coerceIn(0, biomeCellCountY - 1)

          val blockIndex = blockIndex(localX, localZ, blockY, minY, chunkWidth, chunkDepth)
          val cornerIndex = cornerIndex(cellX, cellZ, cellY, biomeCellCountX, biomeCellCountZ)
          val cornerColumnIndex = cornerColumnIndex(cellX, cellZ, biomeCellCountX)

          val mediumCellY = ((blockY - minY) / mediumCellSize).coerceIn(0, mediumCellCountY - 1)
          val mediumCornerIndex = cornerIndex(mediumCellX, mediumCellZ, mediumCellY, mediumCellCountX, mediumCellCountZ)

          val mediumLocalCellOriginY = minY + mediumCellY * mediumCellSize

          val localCellOriginY = minY + cellY * biomeCellSize

          val tx = (((localX - localCellOriginX).toDouble() + 0.5) / biomeCellSize.toDouble()).coerceIn(0.0, 1.0)
          val tz = (((localZ - localCellOriginZ).toDouble() + 0.5) / biomeCellSize.toDouble()).coerceIn(0.0, 1.0)
          val ty = (((blockY - localCellOriginY).toDouble() + 0.5) / biomeCellSize.toDouble()).coerceIn(0.0, 1.0)

          val detail = terrainDetailNoise.noise3D(worldX, blockY, worldZ) * 3.0
          val terrainMacro = interpolate(
            cornerIndex,
            cellX, cellY, cellZ,
            biomeCellCountX, biomeCellCountZ,
            densityTerrainMacroByCorner,
            tx, ty, tz
          ) + detail

          baseTerrainDensityColumn[iy] = terrainMacro
          if (terrainMacro > 0.0) {
            if (!foundBaseSurfaceY) {
              baseSurfaceY = blockY
              //baseSurfaceYColumn[iy] = blockY
              foundBaseSurfaceY = true
            }
          }
          /*if(blockCache[mediumCornerIndex] != null) continue

          val surfaceBlend = surfaceBlendByCornerColumn[cornerColumnIndex]!!

          val c000 = mediumCornerIndex
          val c100 = cornerIndex(mediumCellX + 1, mediumCellZ, mediumCellY, mediumCellCountX, mediumCellCountZ)
          val c010 = cornerIndex(mediumCellX, mediumCellZ + 1, mediumCellY, mediumCellCountX, mediumCellCountZ)
          val c110 = cornerIndex(mediumCellX + 1, mediumCellZ + 1, mediumCellY, mediumCellCountX, mediumCellCountZ)

          val c001 = cornerIndex(mediumCellX, mediumCellZ, mediumCellY + 1, mediumCellCountX, mediumCellCountZ)
          val c101 = cornerIndex(mediumCellX + 1, mediumCellZ, mediumCellY + 1, mediumCellCountX, mediumCellCountZ)
          val c011 = cornerIndex(mediumCellX, mediumCellZ + 1, mediumCellY + 1, mediumCellCountX, mediumCellCountZ)
          val c111 = cornerIndex(mediumCellX + 1, mediumCellZ + 1, mediumCellY + 1, mediumCellCountX, mediumCellCountZ)

          val mtx = (((localX - localMediumCellOriginX).toDouble() + 0.5) / mediumCellSize.toDouble()).coerceIn(0.0, 1.0)
          val mtz = (((localZ - localMediumCellOriginZ).toDouble() + 0.5) / mediumCellSize.toDouble()).coerceIn(0.0, 1.0)
          val mty = (((blockY - mediumLocalCellOriginY).toDouble() + 0.5) / mediumCellSize.toDouble()).coerceIn(0.0, 1.0)
          blockCache[mediumCornerIndex] = surfaceBlend.primaryBiome().caves!!.interpolateCacheUntyped(
            caveCache[c000],
            caveCache[c100],
            caveCache[c010],
            caveCache[c110],
            caveCache[c001],
            caveCache[c101],
            caveCache[c011],
            caveCache[c111],
            mtx, mty, mtz
          )*/
        }

        var setSurfaceY = false
        for (blockY in maxY downTo minY) {
          val iy = blockY - minY
          val cellY = ((blockY - minY) / biomeCellSize).coerceIn(0, biomeCellCountY - 1)
          val blockIndex = blockIndex(localX, localZ, blockY, minY, chunkWidth, chunkDepth)
          val cornerIndex = cornerIndex(cellX, cellZ, cellY, biomeCellCountX, biomeCellCountZ)

          val mediumCellY = ((blockY - minY) / mediumCellSize).coerceIn(0, mediumCellCountY - 1)
          val mediumCornerIndex = cornerIndex(mediumCellX, mediumCellZ, mediumCellY, mediumCellCountX, mediumCellCountZ)

          val mediumLocalCellOriginY = minY + mediumCellY * mediumCellSize

          val cornerColumnIndex = cornerColumnIndex(cellX, cellZ, biomeCellCountX)

          val c000 = cornerIndex
          val c100 = cornerIndex(cellX + 1, cellZ, cellY, biomeCellCountX, biomeCellCountZ)
          val c010 = cornerIndex(cellX, cellZ + 1, cellY, biomeCellCountX, biomeCellCountZ)
          val c110 = cornerIndex(cellX + 1, cellZ + 1, cellY, biomeCellCountX, biomeCellCountZ)

          val c001 = cornerIndex(cellX, cellZ, cellY + 1, biomeCellCountX, biomeCellCountZ)
          val c101 = cornerIndex(cellX + 1, cellZ, cellY + 1, biomeCellCountX, biomeCellCountZ)
          val c011 = cornerIndex(cellX, cellZ + 1, cellY + 1, biomeCellCountX, biomeCellCountZ)
          val c111 = cornerIndex(cellX + 1, cellZ + 1, cellY + 1, biomeCellCountX, biomeCellCountZ)
          val localCellOriginY = minY + cellY * biomeCellSize

          val tx = (((localX - localCellOriginX).toDouble() + 0.5) / biomeCellSize.toDouble()).coerceIn(0.0, 1.0)
          val tz = (((localZ - localCellOriginZ).toDouble() + 0.5) / biomeCellSize.toDouble()).coerceIn(0.0, 1.0)
          val ty = (((blockY - localCellOriginY).toDouble() + 0.5) / biomeCellSize.toDouble()).coerceIn(0.0, 1.0)

          val terrainMacro = baseTerrainDensityColumn[iy]

          /*val cavesMacro = interpolatedCornerDensityAt(
            localX, blockY, localZ,
            minY,
            mediumCellSize,
            mediumCellCountX, mediumCellCountZ,
            mediumCellCountY,
            densityCavesMacroByCorner,
            false
          )*/// + caveDetailNoise.noise3D(worldX, blockY, worldZ) * 0.12

          //val caves = Curve.smoothstep(threshold, threshold + ramp, cavesMacro)

          val surfaceBlend = surfaceBlendByCornerColumn[cornerColumnIndex]!!

          val fineEnv = VolumeEnv(
            baseSurfaceY, 0, 0, 0.0, 0
          )
          val fineTerrain = generation.blendedFineBiomeDensity(
            ctx, surfaceBlend, worldX, blockY, worldZ,
            fineEnv,
            signalWriter
          ).finalDensity()

          val terrain = terrainMacro + fineTerrain

          if (terrain > 0.0) {
            if (!setSurfaceY) {
              setSurfaceY = true
              surfaceYByBlockColumn[columnIndex] = blockY
            }
          }
          val caveCtx = SimpleCaveContext(
            worldX, blockY, worldZ,
            baseSurfaceY, baseSurfaceY - blockY,
            terrain, surfaceBlend.edgeContext,
            signalWriter
          )
          //val caveCache = blockCache[mediumCornerIndex]

          val caveCache = run {
            val c000 = mediumCornerIndex
            val c100 = cornerIndex(mediumCellX + 1, mediumCellZ, mediumCellY, mediumCellCountX, mediumCellCountZ)
            val c010 = cornerIndex(mediumCellX, mediumCellZ + 1, mediumCellY, mediumCellCountX, mediumCellCountZ)
            val c110 = cornerIndex(mediumCellX + 1, mediumCellZ + 1, mediumCellY, mediumCellCountX, mediumCellCountZ)

            val c001 = cornerIndex(mediumCellX, mediumCellZ, mediumCellY + 1, mediumCellCountX, mediumCellCountZ)
            val c101 = cornerIndex(mediumCellX + 1, mediumCellZ, mediumCellY + 1, mediumCellCountX, mediumCellCountZ)
            val c011 = cornerIndex(mediumCellX, mediumCellZ + 1, mediumCellY + 1, mediumCellCountX, mediumCellCountZ)
            val c111 =
              cornerIndex(mediumCellX + 1, mediumCellZ + 1, mediumCellY + 1, mediumCellCountX, mediumCellCountZ)

            val mtx =
              (((localX - localMediumCellOriginX).toDouble() + 0.5) / mediumCellSize.toDouble()).coerceIn(0.0, 1.0)
            val mtz =
              (((localZ - localMediumCellOriginZ).toDouble() + 0.5) / mediumCellSize.toDouble()).coerceIn(0.0, 1.0)
            val mty =
              (((blockY - mediumLocalCellOriginY).toDouble() + 0.5) / mediumCellSize.toDouble()).coerceIn(0.0, 1.0)
            surfaceBlend.primaryBiome().caves!!.interpolateCacheUntyped(
              caveCache[c000],
              caveCache[c100],
              caveCache[c010],
              caveCache[c110],
              caveCache[c001],
              caveCache[c101],
              caveCache[c011],
              caveCache[c111],
              mtx, mty, mtz
            )
          }

          val cavesMacro = generation.blendedBiomeDensityCavesWithCache(
            ctx, surfaceBlend, worldX, blockY, worldZ, signalWriter,
            caveCtx, caveCache
          ).finalDensity() + caveDetailNoise.noise3D(worldX, blockY, worldZ) * 0.12

          /*val caveCtx = SimpleCaveContext(
            worldX, blockY, worldZ,
            baseSurfaceY, baseSurfaceY - blockY,
            terrain, surfaceBlend.edgeContext,
            signalWriter
          )
          val cavesMacro = generation.blendedBiomeDensityCaves(
            ctx, surfaceBlend, worldX, blockY, worldZ,
            signalWriter,caveCtx
          ).finalDensity()*/

          val terrainFinal = terrain + cavesMacro

          //surface Y is before caves
          if (terrain > 0.0) {
            solidNoCavesByBlock[blockIndex] = true

            if (terrainFinal <= 0.0) {
              terrain3D.caveAirByBlock[blockIndex] = true
            }
          }

          val volBlend = VolBiomeBlendSample.interpolateVolBlend(
            volumetricBlendByCorner[c000]!!,
            volumetricBlendByCorner[c100]!!,
            volumetricBlendByCorner[c010]!!,
            volumetricBlendByCorner[c110]!!,
            volumetricBlendByCorner[c001]!!,
            volumetricBlendByCorner[c101]!!,
            volumetricBlendByCorner[c011]!!,
            volumetricBlendByCorner[c111]!!,
            tx, ty, tz
          )
          val volumeEnv = VolumeEnv(
            0, 0, 0, 0.0, 0
          )
          val volStack = generation.blendedVolumetricDensity(
            ctx, volBlend, worldX, blockY, worldZ, volumeEnv,
            signalWriter
          )
          val volumetricContribution = volStack.finalDensity()
          val density = terrainFinal + volumetricContribution

          densityByBlock[blockIndex] = density

          val materialBiome =
            if (!volBlend.isEmpty() && volumetricContribution > 0.01) volBlend.dominant()
            else surfaceBlend.primaryBiome()
          primaryBiomeByBlock[blockIndex] = materialBiome
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
    val mediumCellCountY = (chunkBlockHeight + mediumCellSize - 1) / mediumCellSize

    val cornerArraySize = (biomeCellCountX + 1) * (biomeCellCountZ + 1) * (biomeCellCountY + 1)
    val cornerColumnArraySize = (biomeCellCountX + 1) * (biomeCellCountZ + 1)

    val mediumCornerArraySize = (mediumCellCountX + 1) * (mediumCellCountZ + 1) * (mediumCellCountY + 1)

    val densityTerrainMacroByCorner = DoubleArray(cornerArraySize)
    val densityCavesMacroByCorner = DoubleArray(mediumCornerArraySize)

    val surfaceBlendByCornerColumn = arrayOfNulls<BiomeBlendSample?>(cornerColumnArraySize)
    val volumetricBlendByCorner = arrayOfNulls<VolBiomeBlendSample?>(cornerArraySize)

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

    val caveCache = arrayOfNulls<Any>(mediumCornerArraySize)

    sampleMediumCellCorners(
      generateCtx,
      chunkX, chunkZ,
      surfaceBlendByCornerColumn,
      mediumCellCountX, mediumCellCountZ, mediumCellCountY,
      mediumCellSize, minBlockY, maxBlockY,
      densityTerrainMacroByCorner,
      densityCavesMacroByCorner,
      chunkBlockHeight,
      caveCache
    )

    val blockArraySize = chunkWidth * chunkDepth * chunkBlockHeight
    val blockColumnSize = chunkWidth * chunkDepth

    val densityByBlock = DoubleArray(blockArraySize)
    val primaryBiomeByBlock = arrayOfNulls<Biome?>(blockArraySize)

    val terrain2D = SimpleTerrain2D(
      generation, generateCtx,
      chunkX * chunkWidth - 32,
      chunkZ * chunkDepth - 32,
      chunkWidth + 64,
      chunkDepth + 64
    )

    val surfaceYByBlockColumn = terrain2D.surfaceY//IntArray(blockColumnSize)

    val solidNoCavesByBlock = BitSet(blockArraySize)

    val terrain3D = SimpleTerrain3D(
      generateCtx, BitSet()
    )
    val terrainSnapshot = SimpleTerrainSnapshot(
      terrain2D, terrain3D
    )

    sampleDensity(
      generateCtx,
      chunkX, chunkZ,
      surfaceBlendByCornerColumn,
      biomeCellCountX, biomeCellCountZ, biomeCellCountY,
      mediumCellCountX,
      mediumCellCountZ,
      mediumCellCountY,
      minBlockY, maxBlockY,
      densityTerrainMacroByCorner, densityCavesMacroByCorner,
      volumetricBlendByCorner,
      densityByBlock,
      primaryBiomeByBlock,
      surfaceYByBlockColumn,
      solidNoCavesByBlock,
      terrain3D,
      signalWriter,
      chunkBlockHeight,
      caveCache
    )

    return SimpleSampledChunk(
      generateCtx,
      densityTerrainMacroByCorner,
      densityCavesMacroByCorner,
      terrainSnapshot,
      surfaceBlendByCornerColumn,
      volumetricBlendByCorner,
      surfaceYByBlockColumn,
      densityByBlock,
      primaryBiomeByBlock,
      solidNoCavesByBlock
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

  /*@Deprecated("")
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
  }*/

  /*fun sampleSurfaceColumns(
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
    }*/

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
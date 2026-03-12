package killercreepr.cruxworldgen.core.context

import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.terrain.Terrain2D
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.generation.GenerationPipeline
import killercreepr.cruxworldgen.api.util.MathUtil
import killercreepr.cruxworldgen.api.util.MathUtil.localXFromWorld
import killercreepr.cruxworldgen.api.util.MathUtil.localZFromWorld
import killercreepr.cruxworldgen.core.noise.BaseNoiseKeys

class SimpleTerrain2D(
  val generation : GenerationPipeline,
  val ctx : GenerateContext,
  val minWX: Int, val minWZ: Int,
  val width: Int, val depth: Int
) : Terrain2D {
  val surfaceY = IntArray(width * depth)
  val skySurfaceY = IntArray(width * depth)
  val oceanFloorY = IntArray(width * depth)
  val waterDepth = IntArray(width * depth)

  fun idxUnsafe(wx: Int, wz: Int): Int {
    val x = wx - minWX
    val z = wz - minWZ
    return x + z * width
  }

  fun calculateSurfaceY(worldX: Int, worldZ: Int) : Int{
    val zone = generation.zones.sampleZone(ctx, worldX, worldZ)
    val biomeBlend = zone.biomes.sampleBiomeBlend(ctx, worldX, worldZ)
    return findSurfaceY(ctx, biomeBlend, worldX, worldZ)
  }

  @Deprecated("BAD, not accurate")
  fun findSurfaceY(ctx: GenerateContext, biomeBlend: BiomeBlendSample, worldX: Int, worldZ: Int): Int {
    val minY = ctx.chunkContext.minHeight
    val maxY = ctx.chunkContext.maxHeight - 1

    for (y in maxY downTo minY) {
      val terrainMacro = 0.0//todo generation.blendedBiomeDensity(ctx, biomeBlend, worldX, y, worldZ, SignalHandler.DUMMY).finalDensity()

      val detail = ctx.noise.get(BaseNoiseKeys.TerrainDetail).noise3D(worldX,  y, worldZ) * 3.0
      val terrainFinal = terrainMacro + detail
      if(terrainFinal > 0.0) return y
      /*val terrainDensity = generation.terrainDensityNoCaves(ctx, biomeBlend, worldX, y, worldZ)
      if (terrainDensity > 0.0) return y*/
    }
    return minY
  }

  private fun seaCap(): Int = minOf(ctx.chunkContext.seaLevel, ctx.chunkContext.maxHeight - 1)

  fun calculateSkySurfaceY(worldX: Int, worldZ: Int): Int {
    // In your density model, sky surface == topmost solid.
    return calculateSurfaceY(worldX, worldZ)
  }

  fun calculateOceanFloorY(worldX: Int, worldZ: Int): Int {
    val sY = calculateSurfaceY(worldX, worldZ)
    return if (sY < seaCap()) sY else -1
  }

  fun calculateWaterDepth(worldX: Int, worldZ: Int): Int {
    val sY = calculateSurfaceY(worldX, worldZ)
    val sea = seaCap()
    return if (sY < sea) (sea - sY) else 0
  }


  override fun surfaceY(worldX: Int, worldZ: Int): Int = if (isInBounds(worldX, worldZ)) {
    surfaceY[MathUtil.columnIndex(
      localXFromWorld(worldX, ctx.chunkContext.width),
      localZFromWorld(worldZ, ctx.chunkContext.depth),
      ctx.chunkContext.width
    )]
  } else {
    calculateSurfaceY(worldX, worldZ)//todo cache outside calls
  }

  override fun skySurfaceY(worldX: Int, worldZ: Int): Int = if (isInBounds(worldX, worldZ)) {
    skySurfaceY[idxUnsafe(worldX, worldZ)]
  } else {
    calculateSkySurfaceY(worldX, worldZ)
  }

  override fun oceanFloorY(worldX: Int, worldZ: Int): Int = if (isInBounds(worldX, worldZ)) {
    oceanFloorY[idxUnsafe(worldX, worldZ)]
  } else {
    calculateOceanFloorY(worldX, worldZ)
  }

  override fun waterDepth(worldX: Int, worldZ: Int): Int = if (isInBounds(worldX, worldZ)) {
    waterDepth[idxUnsafe(worldX, worldZ)]
  } else {
    calculateWaterDepth(worldX, worldZ)
  }

  override fun isInBounds(worldX: Int, worldZ: Int): Boolean {
    val x = worldX - minWX
    val z = worldZ - minWZ
    return x in 0 until width && z in 0 until depth
  }

  override fun isOceanColumn(wx: Int, wz: Int): Boolean = waterDepth(wx, wz) > 0
  override fun seaSurfaceY(wx: Int, wz: Int): Int = minOf(ctx.chunkContext.seaLevel, ctx.chunkContext.maxHeight - 1)
  override fun topY(wx: Int, wz: Int): Int = if (isOceanColumn(wx, wz)) seaSurfaceY(wx, wz) else surfaceY(wx, wz)
}
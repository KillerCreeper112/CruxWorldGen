package killercreepr.cruxworldgen.test6

import killercreepr.cruxworldgen.test6.biome.BiomeBlendSample
import killercreepr.cruxworldgen.test6.biome.CaveContext
import killercreepr.cruxworldgen.test6.context.GenerateContext
import killercreepr.cruxworldgen.test6.density.DensityStack
import killercreepr.cruxworldgen.test6.zone.ZoneRegistry

class GenerationPipeline(
  val zones : ZoneRegistry
) {

  fun terrainDensityNoCaves(
    ctx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int
  ): Double {
    val terrainStack = blendedBiomeDensity(ctx, biomeBlend, worldX, y, worldZ)
    val detailDensity = ctx.noise.detail3D(worldX, y, worldZ) * 3.0  // keep or set 0 while tuning
    return terrainStack.finalDensity() + detailDensity
  }
  fun blendedBiomeCarve(
    ctx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int,
    surfaceY: Int,
    terrainDensity: Double
  ): Double {

    var weightedSum = 0.0
    var maxWeight = 0.0001

    val depthBelowSurface = surfaceY - y

    for (wb in biomeBlend.weightedBiomes) {
      maxWeight = maxOf(maxWeight, wb.weight)

      val caveContext = CaveContext(
        worldX = worldX,
        y = y,
        worldZ = worldZ,
        surfaceY = surfaceY,
        depthBelowSurface = depthBelowSurface,
        terrainDensity = terrainDensity,
        edge = biomeBlend.edgeContext
      )

      val carve = wb.biome.caves.carve(ctx, caveContext)
      weightedSum += wb.weight * carve
    }

    // Critical: prevent carve magnitude from shrinking in the middle of a border.
    return weightedSum / maxWeight
  }


  fun blendedBiomeDensity(
    generateCtx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int
  ): DensityStack {
    var blendedBase = 0.0
    var blendedAdd = 0.0
    var blendedCarve = 0.0

    for (weightedBiome in biomeBlend.weightedBiomes) {
      val biome = weightedBiome.biome
      val weight = weightedBiome.weight

      val stack = biome.shape.density(
        generateCtx, worldX, y, worldZ, biomeBlend.edgeContext
      )

      blendedBase += weight * stack.base
      blendedAdd += weight * stack.add
      blendedCarve += weight * stack.carve
    }

    return DensityStack(blendedBase, blendedAdd, blendedCarve)
  }
}
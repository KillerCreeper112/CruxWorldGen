package killercreepr.cruxworldgen.core.generation

import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.volumetric.VolBiomeBlendSample
import killercreepr.cruxworldgen.api.context.volumetric.VolumeEnv
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.generation.GenerationPipeline
import killercreepr.cruxworldgen.api.signal.SignalWriter
import killercreepr.cruxworldgen.api.zone.ZoneRegistry
import killercreepr.cruxworldgen.core.biome.volumetric.VolumetricBiomeRegistry

class SimpleGenerationPipeline(
  override val zones : ZoneRegistry,
  override val volumetricBiomes: VolumetricBiomeRegistry
) : GenerationPipeline {
  override fun blendedBiomeDensityCavesCache(
    generateCtx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int,
    signalWriter : SignalWriter,
    terrainDensity: Double
  ): Any? {
    return biomeBlend.primaryBiome().caves?.coarseCache(
      generateCtx, worldX, y, worldZ, terrainDensity
    )
  }

  override fun blendedBiomeDensityCavesWithCache(
    generateCtx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int,
    signalWriter : SignalWriter,
    caveCtx : CaveContext,
    cache: Any?
  ): DensityStack {
    var blendedAdd = 0.0
    var blendedCarve = 0.0
    val caves = biomeBlend.primaryBiome().caves ?: return DensityStack.emptyStack()
    blendedAdd += caves.addUntyped(generateCtx, caveCtx, cache)
    blendedCarve += caves.carveUntyped(generateCtx, caveCtx, cache)

    /*for (weightedBiome in biomeBlend.weightedBiomes) {
      val biome = weightedBiome.biome
      val caves = biome.caves ?: continue
      val weight = weightedBiome.weight

      blendedAdd += weight * caves.addUntyped(generateCtx, caveCtx, cache)
      blendedCarve += weight * caves.carveUntyped(generateCtx, caveCtx, cache)
    }*/

    return DensityStack.densityStack(
      base = 0.0,
      add = blendedAdd,
      carve = blendedCarve,
    )
  }

  override fun blendedBiomeDensityCaves(
    generateCtx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int,
    signalWriter : SignalWriter,
    caveCtx : CaveContext
  ): DensityStack {
    var blendedAdd = 0.0
    var blendedCarve = 0.0

    for (weightedBiome in biomeBlend.weightedBiomes) {
      val biome = weightedBiome.biome
      val caves = biome.caves ?: continue
      val weight = weightedBiome.weight

      blendedAdd += weight * caves.add(generateCtx, caveCtx)
      blendedCarve += weight * caves.carve(generateCtx, caveCtx)
    }

    return DensityStack.densityStack(
      base = 0.0,
      add = blendedAdd,
      carve = blendedCarve,
    )
  }

  override fun blendedBiomeDensity(
    generateCtx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int,
    signalWriter : SignalWriter
  ): DensityStack {
    var blendedBase = 0.0
    var blendedAdd = 0.0
    var blendedCarve = 0.0

    for (weightedBiome in biomeBlend.weightedBiomes) {
      val biome = weightedBiome.biome
      val weight = weightedBiome.weight
      val shape = biome.shape ?: continue

      val stack = shape.density(
        generateCtx, worldX, y, worldZ, biomeBlend.edgeContext,
        signalWriter
      )

      blendedBase += weight * stack.base
      blendedAdd += weight * stack.add
      blendedCarve += weight * stack.carve
    }

    return DensityStack.densityStack(
      base = blendedBase,
      add = blendedAdd,
      carve = blendedCarve
    )
  }

  override fun blendedFineBiomeDensity(
    generateCtx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int,
    env: VolumeEnv,
    signalWriter: SignalWriter
  ): DensityStack {
    var blendedBase = 0.0
    var blendedAdd = 0.0
    var blendedCarve = 0.0

    for (weightedBiome in biomeBlend.weightedBiomes) {
      val biome = weightedBiome.biome
      val weight = weightedBiome.weight
      val shape = biome.fineShape ?: continue

      val stack = shape.density(
        generateCtx, worldX, y, worldZ, biomeBlend.edgeContext,
        env,
        signalWriter
      )

      blendedBase += weight * stack.base
      blendedAdd += weight * stack.add
      blendedCarve += weight * stack.carve
    }

    return DensityStack.densityStack(
      base = blendedBase,
      add = blendedAdd,
      carve = blendedCarve
    )
  }

  override fun blendedVolumetricDensity(
    ctx: GenerateContext,
    volBlend: VolBiomeBlendSample,
    worldX: Int, y: Int, worldZ: Int,
    env: VolumeEnv,
    signals: SignalWriter
  ): DensityStack {
    var base = 0.0
    var add = 0.0
    var carve = 0.0

    for (wb in volBlend.weighted) {
      //todo val shape = wb.biome.shape(layer) ?: continue
      val s = wb.biome.shape.density(ctx, worldX, y, worldZ, env, signals) ?: continue
      base  += wb.weight * s.base
      add   += wb.weight * s.add
      carve += wb.weight * s.carve
    }

    return DensityStack.densityStack(
      base = 0.0,
      add = add,
      carve = carve
    )
  }
}
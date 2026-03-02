package killercreepr.cruxworldgen.core.generation

import killercreepr.cruxworldgen.api.biome.Biome
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
import killercreepr.cruxworldgen.core.noise.BaseNoiseKeys

class SimpleGenerationPipeline(
  override val zones : ZoneRegistry,
  override val volumetricBiomes: VolumetricBiomeRegistry
) : GenerationPipeline {

  /*override fun sampleBiome(ctx: GenerateContext, biomeBlend : BiomeBlendSample,
                  worldX: Int, y : Int, worldZ: Int,
                  terrainSnapshot : TerrainSnapshot,
                  signalWriter : SignalWriter)
  : Biome{
    val terrainMacro = blendedBiomeDensity(ctx, biomeBlend, worldX, y, worldZ, signalWriter).finalDensity()
    val detail = ctx.noise.get(BaseNoiseKeys.TerrainDetail).noise3D(worldX, y, worldZ) * 3.0
    val terrainFinal = terrainMacro + detail
    val terrain2D = terrainSnapshot.terrain2D
    val surfaceY = terrain2D.surfaceY(worldX, worldZ)
    val env = VolumeEnv(
      surfaceY = surfaceY,
      depthBelowSurface = surfaceY - y,
      heightAboveSurface = y - surfaceY,
      terrainDensity = terrainFinal,
      seaLevel = ctx.chunkContext.seaLevel
    )
    val volBlend = volumetricBiomes.sample(ctx, worldX, y, worldZ, env, signalWriter)

    return if (!volBlend.isEmpty()) volBlend.dominant()
    else biomeBlend.primaryBiome()
  }*/

  override fun resolveMainBiome(
    ctx: GenerateContext,
    signalWriter: SignalWriter,
    worldX: Int,
    y: Int,
    worldZ: Int,
    surfaceY: Int,
    surfaceBlend: BiomeBlendSample,
    // optional: pass a cached blend if you already sampled it
    cachedVolBlend: VolBiomeBlendSample?
  ): Biome {
    // surface-only density for env (important: don’t include vol density here)
    val terrainMacro = blendedBiomeDensity(ctx, surfaceBlend, worldX, y, worldZ, signalWriter).finalDensity()
    val detail = ctx.noise.get(BaseNoiseKeys.TerrainDetail).noise3D(worldX, y, worldZ) * 3.0
    val terrainFinal = terrainMacro + detail

    val env = killercreepr.cruxworldgen.api.context.volumetric.VolumeEnv(
      surfaceY = surfaceY,
      depthBelowSurface = surfaceY - y,
      heightAboveSurface = y - surfaceY,
      terrainDensity = terrainFinal,
      seaLevel = ctx.chunkContext.seaLevel
    )

    val volBlend = cachedVolBlend ?: volumetricBiomes.sample(ctx, worldX, y, worldZ, surfaceBlend,env, signalWriter)

    // pick provider
    return if (!volBlend.isEmpty()) {
      volBlend.dominant()
    } else {
      surfaceBlend.primaryBiome()
    }
  }

  override fun resolveMainBiome3D(
    ctx: GenerateContext,
    signalWriter: SignalWriter,
    worldX: Int,
    y: Int,
    worldZ: Int,
    surfaceY: Int,
    surfaceBlend: BiomeBlendSample
  ): Pair<Biome, VolBiomeBlendSample> {
    // surface-only density for env (important: don’t include vol density here)
    val terrainMacro = blendedBiomeDensity(ctx, surfaceBlend, worldX, y, worldZ, signalWriter).finalDensity()
    val detail = ctx.noise.get(BaseNoiseKeys.TerrainDetail).noise3D(worldX, y, worldZ) * 3.0
    val terrainFinal = terrainMacro + detail

    val env = killercreepr.cruxworldgen.api.context.volumetric.VolumeEnv(
      surfaceY = surfaceY,
      depthBelowSurface = surfaceY - y,
      heightAboveSurface = y - surfaceY,
      terrainDensity = terrainFinal,
      seaLevel = ctx.chunkContext.seaLevel
    )

    val volBlend = volumetricBiomes.sample(ctx, worldX, y, worldZ, surfaceBlend,env, signalWriter)

    // pick provider
    return if (!volBlend.isEmpty()) {
      volBlend.dominant() to volBlend
    } else {
      surfaceBlend.primaryBiome() to volBlend
    }
  }

  override fun terrainDensityNoCaves(
    ctx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int,
    signalWriter : SignalWriter
  ): Double {
    val terrainStack = blendedBiomeDensity(ctx, biomeBlend, worldX, y, worldZ, signalWriter)
    val detailDensity = ctx.noise.get(BaseNoiseKeys.TerrainDetail).noise3D(worldX, y, worldZ) * 3.0
    //val detailDensity = ctx.noise.detail3D(worldX, y, worldZ) * 3.0  // keep or set 0 while tuning
    return terrainStack.finalDensity() + detailDensity
  }
  /*override fun blendedBiomeCarve(
    ctx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int,
    surfaceY: Int,
    terrainDensity: Double,
    signalWriter : SignalWriter
  ): Double {

    var weightedSum = 0.0
    var maxWeight = 0.0001

    val depthBelowSurface = surfaceY - y

    val caveContext = SimpleCaveContext(
      worldX = worldX,
      y = y,
      worldZ = worldZ,
      surfaceY = surfaceY,
      depthBelowSurface = depthBelowSurface,
      terrainDensity = terrainDensity,
      edge = biomeBlend.edgeContext,
      signalWriter = signalWriter
    )

    for (wb in biomeBlend.weightedBiomes) {
      val caves = wb.biome.caves ?: continue
      maxWeight = maxOf(maxWeight, wb.weight)

      val carve = caves.carve(ctx, caveContext)
      weightedSum += wb.weight * carve
    }

    // Critical: prevent carve magnitude from shrinking in the middle of a border.
    return weightedSum / maxWeight
  }
  override fun blendedBiomeAdd(
    ctx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int,
    surfaceY: Int,
    terrainDensity: Double,
    signalWriter : SignalWriter
  ): Double {

    var weightedSum = 0.0
    var maxWeight = 0.0001

    val depthBelowSurface = surfaceY - y

    val caveContext = SimpleCaveContext(
      worldX = worldX,
      y = y,
      worldZ = worldZ,
      surfaceY = surfaceY,
      depthBelowSurface = depthBelowSurface,
      terrainDensity = terrainDensity,
      edge = biomeBlend.edgeContext,
      signalWriter = signalWriter
    )
    for (wb in biomeBlend.weightedBiomes) {
      val caves = wb.biome.caves ?: continue
      maxWeight = maxOf(maxWeight, wb.weight)

      val carve = caves.add(ctx, caveContext)
      weightedSum += wb.weight * carve
    }

    // Critical: prevent carve magnitude from shrinking in the middle of a border.
    return weightedSum / maxWeight
  }*/

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

      val stack = biome.shape.density(
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

  /*override fun blendedVolumetricCarve(
    ctx: GenerateContext,
    volBlend: VolBiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int,
    surfaceY: Int,
    terrainDensity: Double,
    env: VolumeEnv,
    signalWriter : SignalWriter
  ): Double {
    var sum = 0.0
    for (wb in volBlend.weighted) sum += wb.weight * wb.biome.shape.carve(ctx, worldX, y, worldZ, env, signalWriter)
    return sum
  }

  override fun blendedVolumetricAdd(
    ctx: GenerateContext,
    volBlend: VolBiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int,
    surfaceY: Int,
    terrainDensity: Double,
    env: VolumeEnv,
    signalWriter : SignalWriter
  ): Double {
    var sum = 0.0
    for (wb in volBlend.weighted) sum += wb.weight * wb.biome.shape.add(ctx, worldX, y, worldZ, env, signalWriter)
    return sum
  }*/
}
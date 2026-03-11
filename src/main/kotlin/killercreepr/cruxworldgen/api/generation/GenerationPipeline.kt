package killercreepr.cruxworldgen.api.generation

import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.volumetric.VolBiomeBlendSample
import killercreepr.cruxworldgen.api.context.volumetric.VolumeEnv
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.signal.SignalWriter
import killercreepr.cruxworldgen.api.zone.ZoneRegistry
import killercreepr.cruxworldgen.core.biome.volumetric.VolumetricBiomeRegistry

interface GenerationPipeline{
  val zones : ZoneRegistry
  val volumetricBiomes: VolumetricBiomeRegistry

  fun blendedBiomeDensityCavesCache(
    generateCtx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int,
    signalWriter : SignalWriter,
    terrainDensity: Double
  ): Any?

  fun blendedBiomeDensityCavesWithCache(
    generateCtx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int,
    signalWriter : SignalWriter,
    caveCtx : CaveContext,
    cache: Any?
  ): DensityStack

  fun blendedBiomeDensityCaves(
    generateCtx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int,
    signalWriter : SignalWriter,
    caveCtx : CaveContext
  ): DensityStack

  fun blendedBiomeDensity(
    generateCtx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int,
    signalWriter : SignalWriter
  ): DensityStack

  fun blendedFineBiomeDensity(
    generateCtx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int,
    env: VolumeEnv,
    signalWriter : SignalWriter
  ): DensityStack

  fun blendedVolumetricDensity(
    ctx: GenerateContext,
    volBlend: VolBiomeBlendSample,
    worldX: Int, y: Int, worldZ: Int,
    env: VolumeEnv,
    signals: SignalWriter
  ): DensityStack
}
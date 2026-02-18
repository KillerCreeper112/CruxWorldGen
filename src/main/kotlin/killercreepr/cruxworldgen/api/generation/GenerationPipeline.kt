package killercreepr.cruxworldgen.api.generation

import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.signal.SignalWriter
import killercreepr.cruxworldgen.api.zone.ZoneRegistry

interface GenerationPipeline{
  val zones : ZoneRegistry
  fun terrainDensityNoCaves(
    ctx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int,
    signalWriter : SignalWriter
  ): Double
  fun blendedBiomeCarve(
    ctx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int,
    surfaceY: Int,
    terrainDensity: Double,
    signalWriter : SignalWriter
  ): Double
  fun blendedBiomeAdd(
    ctx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int,
    surfaceY: Int,
    terrainDensity: Double,
    signalWriter : SignalWriter
  ): Double

  fun blendedBiomeDensity(
    generateCtx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int,
    signalWriter : SignalWriter
  ): DensityStack
}
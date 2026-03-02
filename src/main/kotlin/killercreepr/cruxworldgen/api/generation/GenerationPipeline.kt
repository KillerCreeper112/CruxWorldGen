package killercreepr.cruxworldgen.api.generation

import killercreepr.cruxworldgen.api.biome.Biome
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
  fun resolveMainBiome(
    ctx: GenerateContext,
    signalWriter: SignalWriter,
    worldX: Int,
    y: Int,
    worldZ: Int,
    surfaceY: Int,
    surfaceBlend: BiomeBlendSample,
    // optional: pass a cached blend if you already sampled it
    cachedVolBlend: VolBiomeBlendSample? = null
  ): Biome
  fun blendedBiomeDensityCaves(
    generateCtx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int,
    signalWriter : SignalWriter,
    caveCtx : CaveContext
  ): DensityStack

  fun resolveMainBiome3D(
    ctx: GenerateContext,
    signalWriter: SignalWriter,
    worldX: Int,
    y: Int,
    worldZ: Int,
    surfaceY: Int,
    surfaceBlend: BiomeBlendSample
  ): Pair<Biome, VolBiomeBlendSample>
  fun terrainDensityNoCaves(
    ctx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int,
    signalWriter : SignalWriter
  ): Double
  /*fun blendedBiomeCarve(
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
  ): Double*/

  fun blendedBiomeDensity(
    generateCtx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int,
    signalWriter : SignalWriter
  ): DensityStack

  val volumetricBiomes: VolumetricBiomeRegistry

  fun blendedVolumetricDensity(
    ctx: GenerateContext,
    volBlend: VolBiomeBlendSample,
    worldX: Int, y: Int, worldZ: Int,
    env: VolumeEnv,
    signals: SignalWriter
  ): DensityStack

  /*fun blendedVolumetricCarve(
    ctx: GenerateContext,
    volBlend: VolBiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int,
    surfaceY: Int,
    terrainDensity: Double,
    env: VolumeEnv,
    signalWriter : SignalWriter
  ): Double
  fun blendedVolumetricAdd(
    ctx: GenerateContext,
    volBlend: VolBiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int,
    surfaceY: Int,
    terrainDensity: Double,
    env: VolumeEnv,
    signalWriter : SignalWriter
  ): Double*/
}
package killercreepr.cruxworldgen.test.biome

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.biome.BiomeShape
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.cave.CaveProfile
import killercreepr.cruxworldgen.api.cave.CaveShape
import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.MaterialContext
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.material.MaterialProvider
import killercreepr.cruxworldgen.api.noise.NoiseBank
import killercreepr.cruxworldgen.api.noise.NoiseField
import killercreepr.cruxworldgen.api.noise.NoiseKey
import killercreepr.cruxworldgen.api.noise.NoiseModule
import killercreepr.cruxworldgen.api.signal.SignalWriter
import killercreepr.cruxworldgen.api.util.HashUtil.hash2D
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import killercreepr.cruxworldgen.core.noise.BaseNoiseKeys
import killercreepr.cruxworldgen.standard.cave.CheeseCaves
import killercreepr.cruxworldgen.standard.cave.NoodleCaves
import killercreepr.cruxworldgen.standard.cave.WormNoodleCaves
import killercreepr.cruxworldgen.test6.prop.test.SimpleTreeDecoration
import org.bukkit.Material
import kotlin.math.*

val gCaves: CaveProfile = CaveProfile(
  listOf(
    CheeseCaves()
    /*LavaTubes(
      noodleRadius = 5.0,
      verticalRadiusBlocks = 10.0
    )*/
    //RavineCarver()
    /*CheeseCaves(
      threshold01 = 0.3
    )*/
    //PillarAdditive(
  )
)

/*val gCaves: CaveShape = CaveProfile(
  listOf(
    SpaghettiCaves(
      baseDepthBelowSurface = 28.0,
      depthVariationBlocks = 14.0
    )
    *//*SpaghettiCaves(
      baseDepthBelowSurface = 50.0,
      depthVariationBlocks = 10.0
    )*//*
  )
)*/


class Plains : Biome.Noised{
  object Noise : NoiseModule{
    object Height2D : NoiseKey{ override val id = "plains.height2D" }

    override fun install(bank: NoiseBank) {
      bank.register(Height2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.001)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(4)
        }
      }
    }
  }
  override val noiseModule = Noise

  override val caves: CaveShape = CaveProfile(listOf(
    WormNoodleCaves(),
  ))
  override val decorations = listOf(
    SimpleTreeDecoration(chancePerPoint = 0.75)
  )

  override val materialProvider = object : MaterialProvider{
    override fun chooseMaterial(context: MaterialContext): BlockData {
      if(context.isSolid){
        return BukkitBlockResolver.INSTANCE.resolve(Material.RED_CONCRETE)
      }
      return BlockData.NONE
    }

  }
  override val shape = object : BiomeShape {
    override fun density(
      ctx: GenerateContext,
      worldX: Int,
      y: Int,
      worldZ: Int,
      edge: BiomeEdgeContext,
      signalWriter : SignalWriter
    ): DensityStack {

      val seaLevelY = ctx.chunkContext.seaLevel
      val hillAmplitudeBlocks = 18.0

      val heightNoiseValue = ctx.noise.get(Noise.Height2D).noise2D(worldX, worldZ) // [-1..1]
      val surfaceY = seaLevelY + (heightNoiseValue * hillAmplitudeBlocks)


      val baseDensity = surfaceY - y.toDouble()

      return DensityStack.densityStack(
        base = baseDensity,
        add = 0.0,
        carve = 0.0
      )
    }
  }

}
class Mountains : Biome.Noised {
  object Noise : NoiseModule{
    object Height2D : NoiseKey{ override val id = "mountain.height2D" }
    object Ridge2D : NoiseKey{ override val id = "mountain.ridge2D" }

    override fun install(bank: NoiseBank) {
      bank.register(Height2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.006)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(5)
        }
      }
      bank.register(Ridge2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.0012)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(4)
        }
      }
    }
  }
  override val noiseModule = Noise

  override val caves: CaveShape = gCaves
  override val materialProvider = object : MaterialProvider {
    override fun chooseMaterial(context: MaterialContext): BlockData {
      return if (context.isSolid) BukkitBlockResolver.INSTANCE.resolve(Material.GREEN_CONCRETE )
      else BlockData.NONE
    }
  }

  override val shape = object : BiomeShape {
    override fun density(
      ctx: GenerateContext,
      worldX: Int,
      y: Int,
      worldZ: Int,
      edge: BiomeEdgeContext,
      signalWriter : SignalWriter
    ): DensityStack {

      val seaLevelY = ctx.chunkContext.seaLevel

      // Blend-friendly base ground (can go above/below sea level)
      val baseGroundAmplitudeBlocks = 24.0
      val baseNoiseValue = ctx.noise.get(Noise.Height2D).noise2D(worldX, worldZ) // [-1..1]
      val baseSurfaceY = seaLevelY + baseNoiseValue * baseGroundAmplitudeBlocks

      // Big mountain stuff (should fade near edges)
      val mountainAmplitudeBlocks = 140.0
      val ridgeAmplitudeBlocks = 80.0

      val uplift01 = (baseNoiseValue + 1.0) * 0.5 // [0..1]
      val upliftHeightBlocks = uplift01 * mountainAmplitudeBlocks

      val ridgeNoiseValue = ctx.noise.get(Noise.Ridge2D).noise2D(worldX, worldZ) // [-1..1]
      val ridge01 = 1.0 - abs(ridgeNoiseValue)           // [0..1]
      val ridgeHeightBlocks = ridge01.pow(3.0) * ridgeAmplitudeBlocks

      val mountainExtraHeightBlocks = upliftHeightBlocks + ridgeHeightBlocks

      //val edgeBlendFactor = edge.edgeBlendFactor()      // 1 at edge, 0 inside
      //val distanceIntoBiome = 1.0 - edgeBlendFactor     // 0 at edge, 1 inside

// Ease-in so mountains ramp up slowly
      //val eased = distanceIntoBiome * distanceIntoBiome * (3.0 - 2.0 * distanceIntoBiome)

// Critical part: don't go to 0 at the edge
      //val minimumEdgeFade = 0.25  // try 0.20..0.40
      //val mountainExtraFade = minimumEdgeFade + (1.0 - minimumEdgeFade) * eased

      val surfaceY = baseSurfaceY + mountainExtraHeightBlocks//baseSurfaceY + mountainExtraHeightBlocks * mountainExtraFade

      val baseDensity = surfaceY - y.toDouble()

      return DensityStack.densityStack(
        base = baseDensity,
        add = 0.0,
        carve = 0.0
      )
    }
  }

}

class Plateaus : Biome.Noised {
  object Noise : NoiseModule{
    object Mask2D : NoiseKey{ override val id = "plateau.mask2d" }
    object Variation2D : NoiseKey{ override val id = "plateau.variation2D" }

    override fun install(bank: NoiseBank) {
      bank.register(Mask2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.0012)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(3)
        }
      }
      bank.register(Variation2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.003)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(4)
        }
      }
    }
  }
  override val noiseModule = Noise
  override val caves: CaveShape = gCaves
  override val materialProvider = object : MaterialProvider {
    override fun chooseMaterial(context: MaterialContext): BlockData {
      return if (context.isSolid) BukkitBlockResolver.INSTANCE.resolve(Material.TERRACOTTA)
      else BlockData.NONE
    }
  }

  override val shape = object : BiomeShape {
    override fun density(
      ctx: GenerateContext,
      worldX: Int,
      y: Int,
      worldZ: Int,
      edge: BiomeEdgeContext,
      signalWriter : SignalWriter
    ): DensityStack {

      val seaLevelY = ctx.chunkContext.seaLevel

      // Base plains-like ground under everything (keeps blending sane)
      val baseGroundAmplitudeBlocks = 18.0
      val baseHeightNoise = ctx.noise.get(Plains.Noise.Height2D).noise2D(worldX, worldZ) // [-1..1]
      val baseSurfaceY = seaLevelY + baseHeightNoise * baseGroundAmplitudeBlocks

      // Plateau parameters
      val plateauTopHeightBlocks = 90.0     // how high plateaus can rise above base
      val plateauTopVariationBlocks = 6.0   // gentle bumps on top
      val plateauEdgeSharpness = 2.2        // higher = sharper plateau edges
      val plateauStepSizeBlocks = 6.0       // quantize top into steps (mesa feel)

      // 1) Mask decides where plateau "wins"
      val maskNoise = ctx.noise.get(Noise.Mask2D).noise2D(worldX, worldZ) // [-1..1]
      val mask01 = (maskNoise + 1.0) * 0.5                        // [0..1]

      // Make mask more binary so it forms regions (tablelands)
      val plateauPresence = mask01.pow(plateauEdgeSharpness)       // [0..1], more 0/1

      // 2) Plateau top variation
      val variationNoise = ctx.noise.get(Noise.Variation2D).noise2D(worldX, worldZ) // [-1..1]
      val topVariation = variationNoise * plateauTopVariationBlocks

      // 3) Compute plateau top height and "step" it
      val rawPlateauTop = baseSurfaceY + plateauTopHeightBlocks + topVariation
      val steppedPlateauTop = floor(rawPlateauTop / plateauStepSizeBlocks) * plateauStepSizeBlocks

      // 4) Fade plateau effect near biome edges so blending doesn't create cliffs
      val edgeBlendFactor = edge.edgeBlendFactor() // 1 at edge, 0 deep inside
      val plateauFade = 1.0 - edgeBlendFactor             // 0 at edge, 1 inside

      // 5) Mix base surface and plateau top using plateauPresence * plateauFade
      val plateauMix = (plateauPresence * plateauFade).coerceIn(0.0, 1.0)
      val surfaceY = (baseSurfaceY * (1.0 - plateauMix)) + (steppedPlateauTop * plateauMix)

      // Signed density:
      var density = surfaceY - y.toDouble()

      // Add small 3D detail (keep it small so plateaus stay flat)
      val detailAmplitudeBlocks = 1.8
      density += ctx.noise.get(BaseNoiseKeys.TerrainDetail).noise3D(worldX, y, worldZ) * detailAmplitudeBlocks

      return DensityStack.densityStack(
        base = density,
        add = 0.0,
        carve = 0.0
      )
    }
  }
}

class SpiralHills : Biome.Noised {
  object Noise : NoiseModule{
    object SwirlCenter : NoiseKey{ override val id = "spiral_hills.swirl.center" }

    override fun install(bank: NoiseBank) {
      bank.register(SwirlCenter){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.0012)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(3)
        }
      }
    }
  }
  override val noiseModule = Noise
  override val caves: CaveShape = gCaves
  override val materialProvider = object : MaterialProvider {
    override fun chooseMaterial(context: MaterialContext): BlockData {
      return if (context.isSolid) BukkitBlockResolver.INSTANCE.resolve(Material.CLAY)
      else BlockData.NONE
    }
  }

  data class SwirlCenter(val centerX: Int, val centerZ: Int)

  fun swirlCenter(worldSeed: Long, worldX: Int, worldZ: Int, cellSizeBlocks: Int): SwirlCenter {
    val cellX = floor(worldX.toDouble() / cellSizeBlocks.toDouble()).toInt()
    val cellZ = floor(worldZ.toDouble() / cellSizeBlocks.toDouble()).toInt()

    val hash = hash2D(worldSeed, cellX, cellZ)
    val offsetX = (hash and 0xFFFF).toInt() % cellSizeBlocks
    val offsetZ = ((hash ushr 16) and 0xFFFF).toInt() % cellSizeBlocks

    val baseX = cellX * cellSizeBlocks
    val baseZ = cellZ * cellSizeBlocks
    return SwirlCenter(baseX + offsetX, baseZ + offsetZ)
  }

  override val shape = object : BiomeShape {
    override fun density(
      ctx: GenerateContext,
      worldX: Int,
      y: Int,
      worldZ: Int,
      edge: BiomeEdgeContext,
      signalWriter : SignalWriter
    ): DensityStack {

      val seaLevelY = ctx.chunkContext.seaLevel

      // --- Base ground (blend-friendly) ---
      val baseGroundAmplitudeBlocks = 14.0
      val baseNoiseValue = ctx.noise.get(Plains.Noise.Height2D).noise2D(worldX, worldZ) // [-1..1]
      val baseSurfaceY = seaLevelY + baseNoiseValue * baseGroundAmplitudeBlocks

      // --- Spiral settings (tune these) ---
      val swirlCellSizeBlocks = 512          // distance between "spiral centers"
      val swirlRadiusBlocks = 220.0          // how far the swirl affects terrain
      val swirlHeightBlocks = 85.0           // max extra height from the swirl
      val spiralArms = 1.0                   // 1 = single spiral, 2 = double spiral
      val twistPerBlock = 0.030              // bigger = tighter swirl
      val ridgeSharpnessPower = 5.0          // bigger = sharper, more stylized ridges

      // Fade swirl near biome edge so transitions don't create walls
      val edgeBlendFactor = edge.edgeBlendFactor() // 1 at edge, 0 inside
      val distanceIntoBiome = 1.0 - edgeBlendFactor
      val swirlFade = distanceIntoBiome * distanceIntoBiome // ease-in

      // --- Determine swirl center for this location ---
      val swirlCenter = swirlCenter(ctx.worldContext.seed, worldX, worldZ, swirlCellSizeBlocks)

      val deltaX = (worldX - swirlCenter.centerX).toDouble()
      val deltaZ = (worldZ - swirlCenter.centerZ).toDouble()

      val radius = sqrt(deltaX * deltaX + deltaZ * deltaZ)
      val normalizedRadius = (radius / swirlRadiusBlocks).coerceIn(0.0, 1.0)

      // 1 at center, 0 at radius
      val radialFalloff = 1.0 - normalizedRadius
      val falloff = radialFalloff * radialFalloff  // smooth falloff

      val angle = atan2(deltaZ, deltaX) // [-pi..pi]

      // Spiral phase: angle + radius-based twist
      val spiralPhase = angle * spiralArms + radius * twistPerBlock

      // Ridge function: 1 at ridge lines, 0 between
      val ridgeRaw = 1.0 - abs(sin(spiralPhase))
      val ridgeSharp = ridgeRaw.pow(ridgeSharpnessPower)

      // Extra height produced by spiral ridges
      val swirlExtraHeight = ridgeSharp * swirlHeightBlocks * falloff * swirlFade

      val surfaceY = baseSurfaceY + swirlExtraHeight

      val baseDensity = surfaceY - y.toDouble()

      return DensityStack.densityStack(
        base = baseDensity,
        add = 0.0,
        carve = 0.0
      )
    }
  }
}

class FjordIce : Biome.Noised {
  object Noise : NoiseModule{
    object Flow2D : NoiseKey{ override val id = "fjord_ice.flow2D" }
    object Lines2D : NoiseKey{ override val id = "fjord_ice.lines2D" }

    override fun install(bank: NoiseBank) {
      bank.register(Flow2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.0012)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(3)
        }
      }
      bank.register(Lines2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.0045)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(4)
        }
      }
    }
  }
  override val noiseModule = Noise
  override val caves: CaveShape = gCaves
  override val materialProvider = object : MaterialProvider {
    override fun chooseMaterial(context: MaterialContext): BlockData {
      // For now: simple debug-friendly.
      // You can upgrade once MaterialContext has surface/depth.
      return if (context.isSolid) BukkitBlockResolver.INSTANCE.resolve(Material.PACKED_ICE)
      else BlockData.NONE
    }
  }

  override val shape = object : BiomeShape {
    override fun density(
      ctx: GenerateContext,
      worldX: Int,
      y: Int,
      worldZ: Int,
      edge: BiomeEdgeContext,
      signalWriter : SignalWriter
    ): DensityStack {

      val seaLevelY = ctx.chunkContext.seaLevel

      // --- Base icy plateau ---
      val plateauBaseHeightBlocks = 55.0      // lifts land above sea
      val plateauVariationBlocks = 18.0       // rolling variation
      val plateauNoise = ctx.noise.get(Plains.Noise.Height2D).noise2D(worldX, worldZ) // [-1..1]
      val plateauSurfaceY =
        seaLevelY + plateauBaseHeightBlocks + plateauNoise * plateauVariationBlocks

      // --- Fjord carving mask ---
      // Use flow noise to warp the line noise so fjords "curve" and look natural
      val flowNoise = ctx.noise.get(Noise.Flow2D).noise2D(worldX, worldZ) // [-1..1]
      val warpStrengthBlocks = 140.0

      val warpedX = worldX + (flowNoise * warpStrengthBlocks).toInt()
      val warpedZ = worldZ + (flowNoise * warpStrengthBlocks).toInt()

      val lineNoise = ctx.noise.get(Noise.Lines2D).noise2D(warpedX, warpedZ) // [-1..1]

      // Convert to "distance from line": abs puts ridges/lines at 0
      val lineDistance = abs(lineNoise) // [0..1-ish]

      // Make fjords narrow: values near 0 become "fjord centers"
      val fjordWidth = 0.22  // smaller = narrower fjords
      val fjordMaskRaw = (1.0 - (lineDistance / fjordWidth)).coerceIn(0.0, 1.0)
      val fjordMask = fjordMaskRaw.pow(3.5) // sharpen valleys

      // --- Fjord depth ---
      val fjordDepthBlocks = 120.0           // how deep the fjords cut
      val fjordCarve = fjordMask * fjordDepthBlocks

      // Surface after fjord carve
      val surfaceY = plateauSurfaceY - fjordCarve

      val baseDensity = surfaceY - y.toDouble()

      return DensityStack.densityStack(
        base = baseDensity,
        add = 0.0,
        carve = 0.0
      )
    }
  }
}

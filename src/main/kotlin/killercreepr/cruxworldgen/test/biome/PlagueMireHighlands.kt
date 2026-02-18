package killercreepr.cruxworldgen.test.biome

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.biome.BiomeShape
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.cave.CaveShape
import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.MaterialContext
import killercreepr.cruxworldgen.api.decor.Decoration
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.material.MaterialProvider
import killercreepr.cruxworldgen.api.noise.NoiseBank
import killercreepr.cruxworldgen.api.noise.NoiseField
import killercreepr.cruxworldgen.api.noise.NoiseKey
import killercreepr.cruxworldgen.api.noise.NoiseModule
import killercreepr.cruxworldgen.api.signal.SignalWriter
import killercreepr.cruxworldgen.api.util.Curve
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import org.bukkit.Material
import kotlin.math.abs
import kotlin.math.pow

class PlagueMireHighlands(
  override val caves: CaveShape = gCaves,
  override val decorations: List<Decoration> = listOf(),
  override val materialProvider: MaterialProvider = object : MaterialProvider {
    override fun chooseMaterial(context: MaterialContext): BlockData {
      return if (context.isSolid) BukkitBlockResolver.INSTANCE.resolve(Material.MUD)
      else BlockData.NONE
    }
  },

  // ----- macro mountain knobs -----
  private val baseAboveSea: Double = 6.0,        // keeps it from being all underwater
  private val baseAmp: Double = 18.0,            // broad hills
  private val mountainAmp: Double = 140.0,       // big mountains
  private val ridgeAmp: Double = 90.0,           // sharp ridges
  private val ridgePower: Double = 3.0,          // higher = thinner ridges

  // ----- bubbly micro knobs (reuse your mire bubble feel) -----
  private val bubbleAmp: Double = 7.0,
  private val bubbleSharpness: Double = 2.2,
  private val pitBias: Double = 0.62,
  private val patchThreshold01: Double = 0.56,
  private val patchFadePower: Double = 1.6,
  private val warpAmpBlocks: Double = 16.0,

  // ----- overhang knobs -----
  private val overhangThreshold01: Double = 0.24, // lower = more overhang carving
  private val overhangAmp: Double = 100.0,         // how hard it carves
  private val overhangMinYBelowSurface: Double = 6.0,   // don’t chew the very top block
  private val overhangMaxYBelowSurface: Double = 70.0,  // don’t carve too deep
  private val cliffSlopeStart: Double = 0.1,     // slope gate (0..1-ish)
  private val cliffSlopeEnd: Double = 0.4        // only overhangs on steep areas
) : Biome.Noised {

  object Noise : NoiseModule{
    object Base2D : NoiseKey{ override val id = "biome.plague_mire_highlands.base2D" }
    object Ridge2D : NoiseKey{ override val id = "biome.plague_mire_highlands.ridge2D" }
    object BubblePatch2D : NoiseKey{ override val id = "biome.plague_mire_highlands.bubble.patch2D" }
    object BubbleVar2D : NoiseKey{ override val id = "biome.plague_mire_highlands.bubble.var2D" }
    object BubbleWarp2D : NoiseKey{ override val id = "biome.plague_mire_highlands.bubble.warp2D" }
    object BubbleCells2D : NoiseKey{ override val id = "biome.plague_mire_highlands.bubble.cells2D" }
    object BubbleSign2D : NoiseKey{ override val id = "biome.plague_mire_highlands.bubble.sign2D" }
    object Overhang3D : NoiseKey{ override val id = "biome.plague_mire_highlands.overhang3D" }

    override fun install(bank: NoiseBank) {
      bank.register(Base2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.005) // broad uplift
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
      bank.register(Ridge2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.003) // ridges
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
      bank.register(BubblePatch2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.0032) // lower = bigger patches
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
      bank.register(BubbleVar2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.020) // higher = more local variation
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
      bank.register(BubbleWarp2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.010) // warp field
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
      bank.register(BubbleCells2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.040) // bubble size (0.03..0.06)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
      bank.register(BubbleSign2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.040) // match bubbleCells2D
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
      bank.register(Overhang3D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.02) // 0.015..0.030 : size of overhang pockets
            .noiseType(CruxNoise.NoiseType.OpenSimplex2) // or OpenSimplex2S if you have it
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
    }
  }
  override val noiseModule = Noise

  override val shape = object : BiomeShape {
    override fun density(
      ctx: GenerateContext,
      worldX: Int,
      y: Int,
      worldZ: Int,
      edge: BiomeEdgeContext,
      signalWriter : SignalWriter
    ): DensityStack {

      val sea = ctx.chunkContext.seaLevel.toDouble()

      // --- 1) macro mountain surface (2D) ---
      val macroSurface = computeMacroSurface(ctx, sea, worldX, worldZ)

      // --- 2) bubbly offset (2D micro heightfield) ---
      val bubbleOffset = computeBubblyOffset(ctx, worldX, worldZ)

      // Combine (you can reduce bubble influence at very high elevations if you want)
      var surface = macroSurface + bubbleOffset

      // --- 3) edge blending: fade micro features near biome edges (prevents seams) ---
      //val edgeBlend = edge.edgeBlendFactor()          // 1 edge, 0 inside
      //val into = 1.0 - edgeBlend
      //val eased = into * into * (3.0 - 2.0 * into)
      val minEdgeInfluence = 0.35
      val microFade = minEdgeInfluence + (1.0 - minEdgeInfluence)// * eased

      val baseOnly = macroSurface
      surface = Curve.lerp(baseOnly, surface, microFade)

      // --- 4) base density from surface ---
      val baseDensity = surface - y.toDouble()

      // --- 5) overhang carve (3D) ---
      // Carve only below the surface in a band (keeps top block intact).
      val depthBelowSurface = (surface - y.toDouble())

      val carve = overhangCarve(ctx, worldX, y, worldZ, surface)


      return DensityStack.densityStack(
        base = baseDensity,
        add = 0.0,
        carve = carve
      )
    }
  }

  private fun computeMacroSurface(ctx: GenerateContext, sea: Double, x: Int, z: Int): Double {
    val wx = x.toDouble()
    val wz = z.toDouble()

    // broad base
    val baseN = ctx.noise.get(Noise.Base2D).noise2D(wx, wz) // [-1,1]
    val baseSurface = sea + baseAboveSea + baseN * baseAmp

    // mountain uplift
    val uplift01 = (baseN + 1.0) * 0.5
    val uplift = uplift01 * mountainAmp

    // ridges (thin lines)
    val ridgeN = ctx.noise.get(Noise.Ridge2D).noise2D(wx, wz) // [-1,1]
    val ridge01 = (1.0 - abs(ridgeN)).coerceIn(0.0, 1.0)
    val ridge = ridge01.pow(ridgePower) * ridgeAmp

    return baseSurface + uplift + ridge
  }

  private fun computeBubblyOffset(ctx: GenerateContext, x: Int, z: Int): Double {
    // Patch clusters
    val patch01 = (ctx.noise.get(Noise.BubblePatch2D).noise2D(x.toDouble(), z.toDouble()) + 1.0) * 0.5
    val patchT = ((patch01 - patchThreshold01) / (1.0 - patchThreshold01)).coerceIn(0.0, 1.0)
    val patchMask = smoothstep01(patchT).pow(patchFadePower)

    // Domain warp
    val wx = x.toDouble()
    val wz = z.toDouble()
    val w1 = ctx.noise.get(Noise.BubbleWarp2D).noise2D(wx, wz) * warpAmpBlocks
    val w2 = ctx.noise.get(Noise.BubbleWarp2D).noise2D(wx + 1000.0, wz + 1000.0) * warpAmpBlocks
    val xw = wx + w1
    val zw = wz + w2

    // Ridged “cells”
    val n = ctx.noise.get(Noise.BubbleCells2D).noise2D(xw, zw)
    val ridge01 = (1.0 - abs(n)).coerceIn(0.0, 1.0)
    val shape = ridge01.pow(bubbleSharpness)

    // pits vs bumps
    val sign01 = (ctx.noise.get(Noise.BubbleSign2D).noise2D(xw, zw) + 1.0) * 0.5
    val isPit = sign01 < pitBias

    val bumpScale = 0.65
    val pitScale = 1.00

    val offset = if (isPit) -bubbleAmp * pitScale * shape else +bubbleAmp * bumpScale * shape
    return offset * patchMask
  }

  private fun overhangCarve(
    ctx: GenerateContext,
    x: Int, y: Int, z: Int,
    surfaceY: Double
  ): Double {

    val sd = surfaceY - y.toDouble() // signed depth (blocks)

    // Only operate slightly above and below the surface boundary.
    // This is the key difference vs pocket-carving.
    if (sd < -1.0 || sd > 14.0) return 0.0

    // A band that is strong just below the surface and fades deeper:
    // - ramps in from sd=-1..2
    // - peaks around sd~4..8
    // - fades out by sd~14
    val inBand  = smoothstep01(((sd + 1.0) / 3.0).coerceIn(0.0, 1.0))        // -1..2
    val outBand = 1.0 - smoothstep01(((sd - 8.0) / 6.0).coerceIn(0.0, 1.0))  // 8..14
    val band = (inBand * outBand).coerceIn(0.0, 1.0)
    if (band <= 0.001) return 0.0

    // Noise mask (3D is important so it varies with Y; 2D tends to make “sausages”)
    val n01 = (ctx.noise.get(Noise.Overhang3D).noise3D(x.toDouble(), y.toDouble(), z.toDouble()) + 1.0) * 0.5
    val t = ((n01 - 0.55) / (1.0 - 0.55)).coerceIn(0.0, 1.0)
    val m = smoothstep01(t)
    if (m <= 0.001) return 0.0

    // Strength needs to beat your added detail and terrain thickness
    val amp = 50.0  // try 20..60

    return amp * band * m
  }
}

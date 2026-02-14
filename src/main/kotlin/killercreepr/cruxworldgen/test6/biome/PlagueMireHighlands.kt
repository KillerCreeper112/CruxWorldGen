package killercreepr.cruxworldgen.test6.biome

import killercreepr.cruxworldgen.test6.context.GenerateContext
import killercreepr.cruxworldgen.api.decor.Decoration
import killercreepr.cruxworldgen.test6.density.DensityStack
import killercreepr.cruxworldgen.test6.material.MaterialContext
import killercreepr.cruxworldgen.test6.material.MaterialProvider
import org.bukkit.Material
import kotlin.math.pow

class PlagueMireHighlands(
  override val caves: CaveShape = gCaves,
  override val decorations: List<Decoration> = listOf(),
  override val materialProvider: MaterialProvider = object : MaterialProvider {
    override fun chooseMaterial(context: MaterialContext): Material {
      return if (context.isSolid) Material.MUD else Material.AIR
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
) : Biome {

  override val shape = object : BiomeShape {
    override fun density(
      ctx: GenerateContext,
      worldX: Int,
      y: Int,
      worldZ: Int,
      edge: BiomeEdgeContext
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
      surface = lerp(baseOnly, surface, microFade)

      // --- 4) base density from surface ---
      val baseDensity = surface - y.toDouble()

      // --- 5) overhang carve (3D) ---
      // Carve only below the surface in a band (keeps top block intact).
      val depthBelowSurface = (surface - y.toDouble())

      val carve = overhangCarve(ctx, worldX, y, worldZ, surface)


      return DensityStack(
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
    val baseN = ctx.noise.mireHighlandsBase2D.noise(wx, wz) // [-1,1]
    val baseSurface = sea + baseAboveSea + baseN * baseAmp

    // mountain uplift
    val uplift01 = (baseN + 1.0) * 0.5
    val uplift = uplift01 * mountainAmp

    // ridges (thin lines)
    val ridgeN = ctx.noise.mireHighlandsRidge2D.noise(wx, wz) // [-1,1]
    val ridge01 = (1.0 - kotlin.math.abs(ridgeN)).coerceIn(0.0, 1.0)
    val ridge = ridge01.pow(ridgePower) * ridgeAmp

    return baseSurface + uplift + ridge
  }

  private fun computeBubblyOffset(ctx: GenerateContext, x: Int, z: Int): Double {
    // Patch clusters
    val patch01 = (ctx.noise.mireBubblePatch2D.noise(x.toDouble(), z.toDouble()) + 1.0) * 0.5
    val patchT = ((patch01 - patchThreshold01) / (1.0 - patchThreshold01)).coerceIn(0.0, 1.0)
    val patchMask = smoothstep01(patchT).pow(patchFadePower)

    // Domain warp
    val wx = x.toDouble()
    val wz = z.toDouble()
    val w1 = ctx.noise.mireBubbleWarp2D.noise(wx, wz) * warpAmpBlocks
    val w2 = ctx.noise.mireBubbleWarp2D.noise(wx + 1000.0, wz + 1000.0) * warpAmpBlocks
    val xw = wx + w1
    val zw = wz + w2

    // Ridged “cells”
    val n = ctx.noise.mireBubbleCells2D.noise(xw, zw)
    val ridge01 = (1.0 - kotlin.math.abs(n)).coerceIn(0.0, 1.0)
    val shape = ridge01.pow(bubbleSharpness)

    // pits vs bumps
    val sign01 = (ctx.noise.mireBubbleSign2D.noise(xw, zw) + 1.0) * 0.5
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
    val n01 = (ctx.noise.mireOverhang3D.noise(x.toDouble(), y.toDouble(), z.toDouble()) + 1.0) * 0.5
    val t = ((n01 - 0.55) / (1.0 - 0.55)).coerceIn(0.0, 1.0)
    val m = smoothstep01(t)
    if (m <= 0.001) return 0.0

    // Strength needs to beat your added detail and terrain thickness
    val amp = 50.0  // try 20..60

    return amp * band * m
  }

  /*private fun smoothstep01(t: Double) = t * t * (3.0 - 2.0 * t)


  private fun computeOverhangCarve(
    ctx: GenerateContext,
    x: Int,
    y: Int,
    z: Int,
    surface: Double,
    baseDensity: Double   // == surface - y
  ): Double {
    // baseDensity is "depth below surface" in blocks
    val d = baseDensity
    if (d < 2.0 || d > 18.0) return 0.0

    // 1) Shallow band mask: peak around ~8 blocks below surface
    // bandUp: 0->1 from d=2..8
    val bandUp = smoothstep01(((d - 2.0) / 6.0).coerceIn(0.0, 1.0))
    // bandDown: 1->0 from d=8..18
    val bandDown = 1.0 - smoothstep01(((d - 8.0) / 10.0).coerceIn(0.0, 1.0))
    val band = (bandUp * bandDown).coerceIn(0.0, 1.0)
    if (band <= 0.001) return 0.0

    // 2) Boundary mask: only carve near the surface boundary so it opens to air
    // when d is small, we’re near the boundary; deep inside mountains we do nothing
    val boundary = 1.0 - smoothstep01(((d - 1.0) / 10.0).coerceIn(0.0, 1.0))
    if (boundary <= 0.001) return 0.0

    // 3) Noise field (warp a bit so it’s not “blobby”)
    val wx = x.toDouble()
    val wz = z.toDouble()
    val w = ctx.noise.mireOverhangWarp2D.noise(wx, wz) * 10.0
    val xw = wx + w
    val zw = wz - w

    val n01 = (ctx.noise.mireOverhang3D.noise(xw, y.toDouble(), zw) + 1.0) * 0.5

    // threshold -> mask
    val t = ((n01 - 0.55) / (1.0 - 0.55)).coerceIn(0.0, 1.0)  // start generous
    val m = smoothstep01(t)
    if (m <= 0.001) return 0.0

    // 4) Strength (this needs to beat your detail noise sometimes)
    val amp = 88.0  // try 20..60 depending on how thick your terrain is

    return m * band * boundary * amp
  }*/


  /*private fun computeOverhangCarve(
    ctx: GenerateContext,
    x: Int,
    y: Int,
    z: Int,
    surface: Double,
    depthBelowSurface: Double
  ): Double {
    // Only carve in a band below the surface (prevents “missing top block”)
    if (depthBelowSurface < overhangMinYBelowSurface) return 0.0
    if (depthBelowSurface > overhangMaxYBelowSurface) return 0.0

    // Gate by cliff steepness: only carve overhangs on steep terrain
    val slope01 = estimateSlope01(ctx, x, z)
    val cliffT = ((slope01 - cliffSlopeStart) / (cliffSlopeEnd - cliffSlopeStart)).coerceIn(0.0, 1.0)
    val cliffMask = smoothstep01(cliffT)
    if (cliffMask <= 0.001) return 0.0

    // Fade within the vertical band (stronger mid-band, weaker at edges)
    val bandT = ((depthBelowSurface - overhangMinYBelowSurface) /
      (overhangMaxYBelowSurface - overhangMinYBelowSurface)).coerceIn(0.0, 1.0)
    val bandMask = smoothstep01(bandT) * smoothstep01(1.0 - bandT)

    // Warp overhang field a bit
    val wx = x.toDouble()
    val wz = z.toDouble()
    val w = ctx.noise.mireOverhangWarp2D.noise(wx, wz) * 10.0
    val xw = wx + w
    val zw = wz - w

    val n01 = (ctx.noise.mireOverhang3D.noise(xw, y.toDouble(), zw) + 1.0) * 0.5
    val t = ((n01 - overhangThreshold01) / (1.0 - overhangThreshold01)).coerceIn(0.0, 1.0)
    if (t <= 0.0) return 0.0

    val carveMask = smoothstep01(t)

    // This is “carve amount” into density space
    return carveMask * overhangAmp * cliffMask * bandMask
  }*/

  private fun estimateSlope01(ctx: GenerateContext, x: Int, z: Int): Double {
    // approximate gradient of macro surface by sampling neighbors
    val sea = ctx.chunkContext.seaLevel.toDouble()

    fun h(xx: Int, zz: Int): Double = computeMacroSurface(ctx, sea, xx, zz)

    val hL = h(x - 2, z)
    val hR = h(x + 2, z)
    val hD = h(x, z - 2)
    val hU = h(x, z + 2)

    val dx = (hR - hL) * 0.25 // /4 (because step is 4 blocks)
    val dz = (hU - hD) * 0.25

    val grad = kotlin.math.sqrt(dx * dx + dz * dz)

    // normalize to 0..1-ish (tune divisor)
    return (grad / 6.0).coerceIn(0.0, 1.0)
  }

  private fun smoothstep01(t: Double): Double = t * t * (3.0 - 2.0 * t)
  private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
}

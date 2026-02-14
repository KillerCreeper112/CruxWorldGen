package killercreepr.cruxworldgen.test6.biome

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
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import org.bukkit.Material
import kotlin.math.pow

class PlagueMire(
  override val caves: CaveShape = gCaves,
  override val decorations: List<Decoration> = listOf(
    // your water fill + bubble columns etc
    // SimpleWaterFillDecoration(),
    // PlagueBubbleColumnsDecoration()
  ),
  override val materialProvider: MaterialProvider = object : MaterialProvider {
    override fun chooseMaterial(context: MaterialContext): BlockData {
      return if (context.isSolid) BukkitBlockResolver.INSTANCE.resolve(Material.BRICK)
      else BlockData.NONE
    }
  },

  // ---- knobs ----
  private val baseAboveSea: Double = 2.0,      // keep it swampy near sea
  private val baseAmp: Double = 6.0,           // broad undulation

  private val bubbleAmp: Double = 8.0,         // total bubble height swing
  private val bubbleSharpness: Double = 2.4,   // higher = rounder domes + deeper pits
  private val pitBias: Double = 0.58,          // 0..1 (higher => more pits than bumps)

  private val patchThreshold01: Double = 0.52, // higher => fewer bubbly zones
  private val patchFadePower: Double = 1.6,    // cluster edge softness

  private val warpAmpBlocks: Double = 18.0,    // how “swirly” the pattern is

  // clamp final surface so it stays mire-like
  private val minSurfaceDelta: Double = -6.0,  // sea + baseAboveSea + this
  private val maxSurfaceDelta: Double = 10.0
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

      // --- 1) broad base ---
      val baseN = 1.0//todo ctx.noise.mireBase2D.noise(worldX.toDouble(), worldZ.toDouble()) // [-1,1]
      var surface = sea + baseAboveSea + baseN * baseAmp

      // --- 2) patch mask (clusters of bubbly terrain) ---
      val patch01 = 1.0//todo (ctx.noise.mireBubblePatch2D.noise(worldX.toDouble(), worldZ.toDouble()) + 1.0) * 0.5
      val patchT = ((patch01 - patchThreshold01) / (1.0 - patchThreshold01)).coerceIn(0.0, 1.0)
      val patchMask = smoothstep01(patchT).pow(patchFadePower)

      // --- 3) domain warp (makes it more organic / less grid-like) ---
      val wx = worldX.toDouble()
      val wz = worldZ.toDouble()
      val w1 = 1.0//todo ctx.noise.mireBubbleWarp2D.noise(wx, wz) * warpAmpBlocks
      val w2 = 1.0//todo ctx.noise.mireBubbleWarp2D.noise(wx + 1000.0, wz + 1000.0) * warpAmpBlocks
      val xw = wx + w1
      val zw = wz + w2

      // --- 4) "bubble cells" from ridged noise ---
      // n in [-1,1] => ridge01 in [0,1] (1 near center of features)
      val n = 1.0//todo ctx.noise.mireBubbleCells2D.noise(xw, zw)
      val ridge01 = (1.0 - kotlin.math.abs(n)).coerceIn(0.0, 1.0)

      // Sharpen => rounder domes/pits
      val bubbleShape = ridge01.pow(bubbleSharpness)

      // Decide bump vs pit
      val sign01 = 1.0//todo (ctx.noise.mireBubbleSign2D.noise(xw, zw) + 1.0) * 0.5
      val isPit = sign01 < pitBias

      // Bumps should be slightly smaller than pits (usually looks more “mire”)
      val bumpScale = 0.65
      val pitScale = 1.00

      val bubbleOffset = if (isPit) {
        -bubbleAmp * pitScale * bubbleShape
      } else {
        +bubbleAmp * bumpScale * bubbleShape
      }

      // Apply only inside patch mask
      surface += bubbleOffset * patchMask

      // --- 5) blend near biome edges so borders aren't harsh ---
      val edgeBlend = edge.edgeBlendFactor()          // 1 at edge, 0 inside
      val into = 1.0 - edgeBlend                      // 0 edge -> 1 inside
      val eased = into * into * (3.0 - 2.0 * into)    // smoothstep
      val minEdgeInfluence = 0.35                     // keeps some identity at borders
      val fade = minEdgeInfluence + (1.0 - minEdgeInfluence) * eased

      // Blend surface back toward base near edges (so micro-bubbles don't make seams)
      val baseOnly = sea + baseAboveSea + baseN * baseAmp
      surface = lerp(baseOnly, surface, fade)

      // --- 6) clamp so it stays “mire near sea” ---
      val minY = sea + baseAboveSea + minSurfaceDelta
      val maxY = sea + baseAboveSea + maxSurfaceDelta
      surface = surface.coerceIn(minY, maxY)

      val baseDensity = surface - y.toDouble()
      return DensityStack.densityStack(base = baseDensity, add = 0.0, carve = 0.0)
    }
  }

  private fun smoothstep01(t: Double): Double = t * t * (3.0 - 2.0 * t)
  private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
}


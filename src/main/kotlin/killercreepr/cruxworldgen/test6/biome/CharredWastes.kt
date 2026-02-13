package killercreepr.cruxworldgen.test6.biome

import killercreepr.cruxworldgen.test6.context.GenerateContext
import killercreepr.cruxworldgen.test6.decor.Decoration
import killercreepr.cruxworldgen.test6.density.DensityStack
import killercreepr.cruxworldgen.test6.material.MaterialContext
import killercreepr.cruxworldgen.test6.material.MaterialProvider
import org.bukkit.Material
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

class CharredWastes(
  override val caves: CaveShape = gCaves,
  override val decorations: List<Decoration> = listOf(MagmaFissureDecoration()),
  override val materialProvider: MaterialProvider = object : MaterialProvider {
    override fun chooseMaterial(context: MaterialContext): Material {
      if (!context.isSolid) return Material.AIR


      // Recompute the same 2D fissure mask the shape uses (world-based, deterministic)
      val fissure = fissureMask01(context.worldX, context.worldZ, context.surfaceY, context.y, context)

      // Paint magma seams in/near fissures.
      // (These are SOLID blocks; later you can add real lava fluid with a decoration pass.)
      if (fissure > 0.55 && context.depthBelowSurface >= 2) {
        // More magma deeper down; more basalt near surface
        return if (context.depthBelowSurface > 10) Material.MAGMA_BLOCK else Material.BASALT
      }

      // Cracked-looking surface palette
      if (context.depthBelowSurface <= 0) return Material.BLACKSTONE
      if (context.depthBelowSurface <= 2) return Material.BASALT

      // Bulk filler
      return Material.BLACKSTONE
    }

    private fun fissureMask01(wx: Int, wz: Int, surfaceY: Int, y: Int, context: MaterialContext): Double {
      // Must match the biome shape's fissure mask parameters (copy/paste)
      val x = wx.toDouble()
      val z = wz.toDouble()
      val ctxRef = context.generateContext

      // domain warp so fissures meander
      val warpAmp = 18.0
      val warpX = ctxRef.noise.charredFissureWarp2D(x, z) * warpAmp
      val warpZ = ctxRef.noise.charredFissureWarp2D(x + 1000.0, z + 1000.0) * warpAmp
      val xw = x + warpX
      val zw = z + warpZ

      // ribbon lines: ridge = 1 - abs(noise)
      val ridge01 = 1.0 - abs(ctxRef.noise.charredFissureMask2D(xw, zw))

      // threshold -> thin lines
      val threshold01 = 0.83
      val t = ((ridge01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
      val line = smoothstep01(t)

      // vertical presence (strong near surface down to depth)
      val depth = 40.0
      val d = (surfaceY - y).toDouble()
      val vertical = smoothstep01(((depth - d) / depth).coerceIn(0.0, 1.0))

      return (line * vertical).coerceIn(0.0, 1.0)
    }

    private fun smoothstep01(t: Double): Double = t * t * (3.0 - 2.0 * t)
  },

  // ===== Plateau knobs =====
  private val baseHeight: Double = 90.0,     // how high above sea level the wastes sit
  private val rollAmp: Double = 18.0,        // broad rolling
  private val ridgeAmp: Double = 55.0,       // raised ridges/knuckles

  // ===== Crack knobs (height depressions) =====
  private val crackThreshold01: Double = 0.70, // higher => fewer cracks
  private val crackDepth: Double = 12.0,       // how deep the cracks depress the surface
  private val crackWarpAmp: Double = 14.0,     // meander cracks

  // ===== Fissure carve knobs =====
  private val fissureThreshold01: Double = 0.83, // higher => rarer/thinner fissures
  private val fissureDepth: Double = 40.0,       // how far down the slit carves
  private val fissureStrength: Double = 26.0,    // how strongly it punches open
  private val fissureWallSoftness: Double = 1.6  // higher => sharper walls
) : Biome {

  override val shape = object : BiomeShape {
    override fun density(
      ctx: GenerateContext,
      worldX: Int,
      y: Int,
      worldZ: Int,
      edge: BiomeEdgeContext
    ): DensityStack {

      // Bind ctx into the material provider so it can sample the same fissure noise
      (materialProvider as? Any)?.let {
        if (it is kotlin.Any) {
          // safe cast to our anonymous provider's method via reflection isn't worth it;
          // simplest: make materialProvider a named class if you want this clean.
        }
      }

      val sea = ctx.chunkContext.seaLevel

      // ----- 1) High-elevation plateau base -----
      val roll = ctx.noise.charredBase2D(worldX, worldZ) // [-1..1]
      val rollY = roll * rollAmp

      val ridgeN = ctx.noise.charredRidge2D(worldX, worldZ) // [-1..1]
      val ridge01 = (1.0 - abs(ridgeN)).pow(3.0)            // [0..1]
      val ridgeY = ridge01 * ridgeAmp

      var surfaceY = (sea + baseHeight + rollY + ridgeY)

      // ----- 2) Crack depressions (heightfield carving, NOT caves) -----
      val x = worldX.toDouble()
      val z = worldZ.toDouble()

      val crackWarpX = ctx.noise.charredCrackWarp2D(x, z) * crackWarpAmp
      val crackWarpZ = ctx.noise.charredCrackWarp2D(x + 777.0, z + 777.0) * crackWarpAmp
      val xw = x + crackWarpX
      val zw = z + crackWarpZ

      val crackRidge01 = 1.0 - abs(ctx.noise.charredCrackMask2D(xw, zw))
      val ct = ((crackRidge01 - crackThreshold01) / (1.0 - crackThreshold01)).coerceIn(0.0, 1.0)
      val crackLine = smoothstep01(ct) // 0..1 near crack center

      // widen a bit + make cracks feel “broken”
      val crackShape = crackLine.pow(1.25)

      // depress surface where crack exists
      surfaceY -= crackShape * crackDepth

      // ----- 3) Magma fissure SLITS (macro carve) -----
      // Similar to cracks but used as a density carve so it opens to sky.
      val fissure01 = fissureMask01(ctx, worldX, y, worldZ, surfaceY)
      val fissureCarve = fissure01.pow(fissureWallSoftness) * fissureStrength

      // Base density is simple surface field
      val baseDensity = surfaceY - y.toDouble()

      return DensityStack(
        base = baseDensity,
        add = 0.0,
        carve = fissureCarve
      )
    }
  }

  private fun fissureMask01(ctx: GenerateContext, wx: Int, y: Int, wz: Int, surfaceY: Double): Double {
    val x = wx.toDouble()
    val z = wz.toDouble()

    // domain warp so fissures meander
    val warpAmp = 18.0
    val warpX = ctx.noise.charredFissureWarp2D(x, z) * warpAmp
    val warpZ = ctx.noise.charredFissureWarp2D(x + 1000.0, z + 1000.0) * warpAmp
    val xw = x + warpX
    val zw = z + warpZ

    // ribbon lines: ridge = 1 - abs(noise)
    val ridge01 = 1.0 - abs(ctx.noise.charredFissureMask2D(xw, zw))

    val t = ((ridge01 - fissureThreshold01) / (1.0 - fissureThreshold01)).coerceIn(0.0, 1.0)
    val line = smoothstep01(t) // 0..1 around slit

    // vertical presence: strong near surface, fades out after fissureDepth
    val d = (surfaceY - y.toDouble())
    val vertical = smoothstep01(((fissureDepth - d) / fissureDepth).coerceIn(0.0, 1.0))

    return (line * vertical).coerceIn(0.0, 1.0)
  }

  private fun smoothstep01(t: Double): Double = t * t * (3.0 - 2.0 * t)
  private fun floorDiv(a: Int, b: Int): Int = floor(a.toDouble() / b.toDouble()).toInt()
}

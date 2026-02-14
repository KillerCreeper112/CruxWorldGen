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

class ToxicFogBasins(
  override val caves: CaveShape = gCaves,
  override val decorations: List<Decoration> = listOf(),
  override val materialProvider: MaterialProvider = object : MaterialProvider {
    override fun chooseMaterial(context: MaterialContext): BlockData {
      return if (context.isSolid) BukkitBlockResolver.INSTANCE.resolve(Material.DARK_PRISMARINE)
      else BlockData.NONE
    }
  },

  // --- Terrain knobs ---
  private val baseYAboveSea: Double = 34.0,      // baseline surface for this biome
  private val warpAmpBlocks: Double = 55.0,      // how “meandering” basins get

  private val basinThreshold01: Double = 0.48,   // lower => more basins; higher => fewer
  private val maxDepthBlocks: Double = 46.0,     // how deep basin centers can be
  private val depthPower: Double = 2.2,          // higher => steeper sides, flatter bottom

  // rim band near the edge of basins
  private val rimWidth01: Double = 0.07,         // thickness of rim band in noise-space
  private val rimHeightBlocks: Double = 7.0,
  private val rimPower: Double = 1.6,

  // floor imperfections
  private val floorAmpBlocks: Double = 2.5
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
      val baseSurfaceY = sea + baseYAboveSea

      val offset = basinOffset(ctx, worldX, worldZ)

      val surfaceY = baseSurfaceY + offset
      val baseDensity = surfaceY - y.toDouble()

      return DensityStack.densityStack(base = baseDensity, add = 0.0, carve = 0.0)
    }
  }

  private fun basinOffset(ctx: GenerateContext, x: Int, z: Int): Double {
    val wx = x.toDouble()
    val wz = z.toDouble()

    // --- 1) Domain warp (continuous) ---
    val warpX = 1.0//todo ctx.noise.basinWarp2D.noise(wx, wz) * warpAmpBlocks
    val warpZ = 1.0//todo ctx.noise.basinWarp2D.noise(wx + 1000.0, wz + 1000.0) * warpAmpBlocks
    val xw = wx + warpX
    val zw = wz + warpZ

    // --- 2) Basin patch field (0..1) ---
    val n01 = 1.0//todo (ctx.noise.basinMask2D.noise(xw, zw) + 1.0) * 0.5

    // We want basins where n01 is LOW.
    // strength01: 0 outside basins, 1 at deepest basin centers.
    val raw = ((basinThreshold01 - n01) / basinThreshold01).coerceIn(0.0, 1.0)
    if (raw <= 0.0) return 0.0

    val basinStrength = smoothstep01(raw).pow(depthPower)

    // --- 3) Bowl depth (negative) ---
    var offset = -maxDepthBlocks * basinStrength

    // --- 4) Rim lip: a band near n01 ~= basinThreshold01 ---
    // Compute how close we are to the edge (centered at threshold).
    val edgeDist = kotlin.math.abs(n01 - basinThreshold01)
    val band = (1.0 - (edgeDist / rimWidth01)).coerceIn(0.0, 1.0)
    val rim = smoothstep01(band).pow(rimPower)

    // only add rim where we are in/near a basin (so it doesn’t appear everywhere)
    offset += rimHeightBlocks * rim * (basinStrength.coerceIn(0.0, 1.0))

    // --- 5) Floor imperfections (mostly in the interior) ---
    val floorN = 1.0//todo ctx.noise.basinFloor2D.noise(wx, wz) // [-1..1]
    offset += floorN * floorAmpBlocks * basinStrength

    return offset
  }

  private fun smoothstep01(t: Double): Double = t * t * (3.0 - 2.0 * t)
}

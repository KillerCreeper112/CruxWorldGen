package killercreepr.cruxworldgen.test3

import killercreepr.cruxstructures.api.structure.generation.StructureGenerator
import org.bukkit.Material

class JaggedHighlandsBiome : CruxBiome {
  override val id: String = "usurvive:jagged_highlands"

  override fun suitability(ctx: BiomeContext, x: Int, z: Int): Double =
    ctx.noise.biome2D("highlands", x, z)

  override fun densityLandforms(): List<DensityLandform> = listOf(
    VariableThicknessHeightLandform(
      baseHeight = { c, x0, z0 ->
        val macro = c.noise.low2D(x0, z0)                 // [0..1]
        val macroTerm = (macro - 0.5) * 2.0               // [-1..1]
        val ridge = c.noise.ridge2D(x0, z0).coerceIn(0.0, 1.0)

        // Mountain regions mask (clusters)
        val m = c.noise.biome2D("highlands_mountains", x0, z0) // [0..1]
        val mountain = smoothstep(0.40, 0.70, m)

        val base = 72.0 + macroTerm * 18.0               // normal ground
        val lift = mountain * (70.0 + ridge * 80.0)      // big peaks in clusters

        base + lift
      },
      thickness = { c, x0, z0 ->
        val ridge = c.noise.ridge2D(x0, z0).coerceIn(0.0, 1.0)
        16.0 * (1.0 - 0.7 * ridge).coerceIn(0.3, 1.0)
      }
    )
  )


  override fun densityCarvers(): List<DensityCarver> = listOf(
    OverhangCarver(id = "highlands", threshold = 0.1, strength = 2.2),
    CaveCarver(id = "highlands", threshold = 0.4, strength = 0.9)
  )

  override fun densityAdditives(): List<DensityAdditive> = emptyList()

  override fun decorators(): List<Decorator> = emptyList()
  override fun structures(): List<StructureGenerator> = emptyList()
  override fun surfaceRule(): SurfaceRule = JaggedHighlandsSurface()
}

class VariableThicknessHeightLandform(
  private val baseHeight: (DensityCtx, Int, Int) -> Double,
  private val thickness: (DensityCtx, Int, Int) -> Double,
  private val borderSoftness: Double = 0.0 // 0.8–2.0 typical if enabled
) : DensityLandform {

  override fun sample(ctx: DensityCtx, x: Int, y: Int, z: Int, out: DensityStack, w: Double) {
    if (w <= 0.0) return

    val h = baseHeight(ctx, x, z)
    val t0 = thickness(ctx, x, z).coerceAtLeast(0.001)

    // Requires DensityCtx to have mix
    val t = t0 * (1.0 + ctx.mix.border * borderSoftness)

    out.addBase(((h - y.toDouble()) / t) * w)
  }
}



class JaggedHighlandsSurface : SurfaceRule {
  override fun material(ctx: SurfaceContext): Material {
    // Top block
    if (ctx.depthFromTop == 0) {
      return if (ctx.slope > 0.65) Material.STONE else Material.GRASS_BLOCK
    }

    // A few blocks of dirt under grass
    if (ctx.depthFromTop <= 3) return Material.DIRT

    // Everything else
    return Material.STONE
  }
}



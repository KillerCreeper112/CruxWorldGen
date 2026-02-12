package killercreepr.cruxworldgen.test3

import killercreepr.cruxstructures.api.structure.generation.StructureGenerator
import org.bukkit.Material
import kotlin.math.abs
import kotlin.math.max

class IceFjordBiome : CruxBiome {
  override val id: String = "usurvive:ice_fjord"

  override fun suitability(ctx: BiomeContext, x: Int, z: Int): Double =
    ctx.noise.biome2D("ice_fjord", x, z)

  override fun densityLandforms(): List<DensityLandform> = listOf(
    HeightToDensityLandform(
      baseHeight = { c, x0, z0 ->
        val basin = c.noise.low2D(x0, z0)   // [0..1]
        val ridge = c.noise.ridge2D(x0, z0) // [0..1]
        // Deep dips with some variation
        val basinTerm = -(basin - 0.5) * 2.0   // [-1..1]
        val ridgeTerm = (ridge - 0.5) * 2.0    // [-1..1]

        -40.0 + (basinTerm * 25.0) + (ridgeTerm * 8.0)
      },
      thickness = {c, x, z -> 10.0}
    )
  )

  override fun densityCarvers(): List<DensityCarver> = listOf(
    // light cave carving so it doesn't obliterate the fjord walls
    CaveCarver(id = "ice_fjord", threshold = 0.45, strength = 0.7)
  )

  override fun densityAdditives(): List<DensityAdditive> = listOf(
    // Ice pillars/spires (dominant-gated by core in pipeline)
    DensityAdditive { ctx, x, y, z, out, core ->
      if (core <= 0.0) return@DensityAdditive

      val cell = ctx.noise.cellular2D(x, z) // [0..1]
      val mask = ((cell - 0.72) / (1.0 - 0.72)).coerceIn(0.0, 1.0)

      // Taper pillars upward: strong near lower Y, fades higher
      val taper = ((80.0 - y) / 80.0).coerceIn(0.0, 1.0)

      out.addAdditive(mask * taper * 2.8 * core)
    }
  )

  override fun surfaceRule(): SurfaceRule = SurfaceRule { ctx ->
    when {
      ctx.depthFromTop == 0 && ctx.slope > 0.65 -> Material.BLUE_ICE
      ctx.depthFromTop == 0 -> Material.PACKED_ICE
      ctx.depthFromTop <= 4 -> Material.ICE
      else -> Material.BLUE_ICE
    }
  }

  override fun decorators(): List<Decorator> = emptyList()
  override fun structures(): List<StructureGenerator> = emptyList()
}

package killercreepr.cruxworldgen.test3

import killercreepr.cruxstructures.api.structure.generation.StructureGenerator
import org.bukkit.Material

class PlainsBiome : CruxBiome {
  override val id: String = "usurvive:plains"

  override fun suitability(ctx: BiomeContext, x: Int, z: Int): Double =
    ctx.noise.biome2D("plains", x, z)

  override fun densityLandforms(): List<DensityLandform> = listOf(
    HeightToDensityLandform(
      baseHeight = { c, x0, z0 ->
        // Very subtle variation so it isn't a perfectly flat sheet
        val gentle = c.noise.low2D(x0, z0) // [0..1]
        62.0 + (gentle - 0.5) * 2.0       // ~59..65
      },
      thickness = {c, x, z -> 14.0} // large thickness = soft slopes / flatter feel
    )
  )

  override fun densityCarvers(): List<DensityCarver> = emptyList()
  override fun densityAdditives(): List<DensityAdditive> = emptyList()

  override fun surfaceRule(): SurfaceRule = SurfaceRule { ctx ->
    when {
      ctx.depthFromTop == 0 -> Material.RED_CONCRETE
      ctx.depthFromTop <= 3 -> Material.MAGMA_BLOCK
      else -> Material.GLOWSTONE
    }
  }

  override fun decorators(): List<Decorator> = emptyList()
  override fun structures(): List<StructureGenerator> = emptyList()
}

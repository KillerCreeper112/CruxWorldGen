package killercreepr.cruxworldgen.test3

import killercreepr.cruxstructures.api.structure.generation.StructureGenerator
import org.bukkit.Material
import kotlin.math.abs

class PlateauMinesBiome : CruxBiome {
  override val id: String = "usurvive:plateau_mines"

  override fun suitability(ctx: BiomeContext, x: Int, z: Int): Double =
    ctx.noise.biome2D("plateau_mines", x, z)

  override fun densityLandforms(): List<DensityLandform> = listOf(
    HeightToDensityLandform(
      baseHeight = { c, x0, z0 ->
        val macro = c.noise.low2D(x0, z0)                 // [0..1]
        val macroTerm = (macro - 0.5) * 2.0               // [-1..1]
        val ridge = c.noise.ridge2D(x0, z0).coerceIn(0.0, 1.0)

        // baseline ~90, plus big variation, plus extra ridge lift
        90.0 + macroTerm * 55.0 + ridge * 60.0
      },
      thickness = { c, x0, z0 ->
        val ridge = c.noise.ridge2D(x0, z0).coerceIn(0.0, 1.0)
        14.0 * (1.0 - 0.65 * ridge).coerceIn(0.35, 1.0)
      }

    )
  )


  override fun densityCarvers(): List<DensityCarver> = listOf(
    // Big caves / hollowing for mines vibe
    CaveCarver(id = "plateau_mines", threshold = 0.35, strength = 1.25)
  )

  override fun densityAdditives(): List<DensityAdditive> = emptyList()

  override fun surfaceRule(): SurfaceRule = SurfaceRule { ctx ->
    when {
      ctx.depthFromTop == 0 && ctx.slope > 0.7 -> Material.TERRACOTTA
      ctx.depthFromTop == 0 -> Material.YELLOW_TERRACOTTA
      ctx.depthFromTop <= 3 -> Material.GRAY_TERRACOTTA
      else -> Material.BLUE_TERRACOTTA
    }
  }

  override fun decorators(): List<Decorator> = emptyList()
  override fun structures(): List<StructureGenerator> = emptyList()
}

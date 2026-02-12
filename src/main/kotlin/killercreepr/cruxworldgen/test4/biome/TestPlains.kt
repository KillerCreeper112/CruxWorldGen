package killercreepr.cruxworldgen.test4.biome

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.test4.CaveForm
import killercreepr.cruxworldgen.test4.Density
import killercreepr.cruxworldgen.test4.DensityContext
import killercreepr.cruxworldgen.test4.DensityForm
import killercreepr.cruxworldgen.test4.Section
import killercreepr.cruxworldgen.test4.Surface
import killercreepr.cruxworldgen.test4.info.SectionCtx
import org.bukkit.Material

class PlainsTest : Section {
  override fun suitability(
    ctx: SectionCtx,
    x: Int,
    z: Int
  ): Double = ctx.noise.biome2D("plains", x, z)

  override fun densityForms(): List<DensityForm> = listOf(
    PlainsDensity()
  )

  override fun caveForms(): List<CaveForm> = listOf(

  )

  override fun surface(): Surface = Surface{ ctx ->
    val density = ctx.density
    if(density <= 0) return@Surface
    ctx.setBlock(Material.STONE)
  }
}

class PlainsDensity : DensityForm{
  override fun sample(
    ctx: DensityContext,
    x: Int,
    y: Int,
    z: Int,
    out: Density,
    weight: Double
  ) {
    val terrain2D = CruxNoise.fast().frequency(0.001).fractalOctaves(2)
      .noise(x.toDouble(),  z.toDouble()) // or a true 2D call if you have it

    val height = 64.0 + (terrain2D * 20.0) // if terrain2D is 0..1, adjust first
    val base = (height - y) / 8.0

    val detail = CruxNoise.fast().frequency(0.02).fractalOctaves(1)
      .noise(x.toDouble(), y.toDouble(), z.toDouble()) * 0.15

    out.addBase(base + detail)

  }
}
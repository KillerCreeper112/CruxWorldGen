package killercreepr.cruxworldgen.test4

import killercreepr.cruxworldgen.test4.info.GenContext
import killercreepr.cruxworldgen.test4.info.SectionCtx


class GenPipeline(
  val resolver: SectionReg
) {
  fun density(genCtx : GenContext, x: Int, y: Int, z: Int): Double {
    val mix = resolver.selectBiomes(
      SectionCtx(genCtx.worldContext, genCtx.noise, genCtx.chunkContext),
      x, z
    )

    val out = Density()

    val ctx = DensityContext(genCtx.worldContext.seed, genCtx.noise)

    // Landforms (BLEND)
    for (section in mix.sections) {
      for (form in section.key.densityForms()) {
        form.sample(ctx, x, y, z, out, section.value)
      }
    }

    // CAVES (BLEND)
    for (section in mix.sections) {
      for (form in section.key.caveForms()) {
        form.sample(ctx, x, y, z, out, section.value)
      }
    }

    return out.finalDensity()
  }
}
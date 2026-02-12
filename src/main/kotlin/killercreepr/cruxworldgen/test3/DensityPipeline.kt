package killercreepr.cruxworldgen.test3

class DensityPipeline(
  val resolver: BiomeResolver
) {

  fun density(seed: Long, noise: NoiseBank, x: Int, y: Int, z: Int): Double {
    val mix = resolver.mix(BiomeContext(seed, noise), x, z)
    val ctx = DensityCtx(seed, noise, mix)

    val out = DensityStack()


    // Landforms (BLEND)
    for (lf in mix.a.densityLandforms()) lf.sample(ctx, x, y, z, out, mix.gwa)
    for (lf in mix.b.densityLandforms()) lf.sample(ctx, x, y, z, out, mix.gwb)

    // Carvers (DOMINANT)
    for (cv in mix.a.densityCarvers()) cv.sample(ctx, x, y, z, out, mix.coreA)
    for (cv in mix.b.densityCarvers()) cv.sample(ctx, x, y, z, out, mix.coreB)

    // Additives (DOMINANT)
    for (ad in mix.a.densityAdditives()) ad.sample(ctx, x, y, z, out, mix.coreA)
    for (ad in mix.b.densityAdditives()) ad.sample(ctx, x, y, z, out, mix.coreB)

    return out.finalDensity()
  }

}

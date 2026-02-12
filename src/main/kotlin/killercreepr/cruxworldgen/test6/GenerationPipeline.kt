package killercreepr.cruxworldgen.test6

import killercreepr.cruxworldgen.test6.biome.BiomeBlendSample
import killercreepr.cruxworldgen.test6.context.GenerateContext
import killercreepr.cruxworldgen.test6.density.DensityStack
import killercreepr.cruxworldgen.test6.zone.ZoneRegistry

class GenerationPipeline(
  val zones : ZoneRegistry
) {
  fun baseDensity(ctx : GenerateContext, x : Int, y : Int, z : Int) : Double{
    val noise = ctx.noise
    return noise.continental(x, y, z) + noise.temperature(x, y, z) +
      noise.humidity(x, y, z) + noise.weirdness(x, y, z)
  }


  fun blendedBiomeDensity(
    generateCtx: GenerateContext,
    biomeBlend: BiomeBlendSample,
    worldX: Int,
    y: Int,
    worldZ: Int
  ): DensityStack {
    var blendedBase = 0.0
    var blendedAdd = 0.0
    var blendedCarve = 0.0

    for (weightedBiome in biomeBlend.weightedBiomes) {
      val biome = weightedBiome.biome
      val weight = weightedBiome.weight

      val stack = biome.shape.density(
        generateCtx, worldX, y, worldZ, biomeBlend.edgeContext
      )

      blendedBase += weight * stack.base
      blendedAdd += weight * stack.add
      blendedCarve += weight * stack.carve
    }

    return DensityStack(blendedBase, blendedAdd, blendedCarve)
  }


  fun blendStacks(weightedStacks: List<Pair<Double, DensityStack>>): DensityStack {
    var blendedBase = 0.0
    var blendedAdd = 0.0
    var blendedCarve = 0.0

    for ((weight, stack) in weightedStacks) {
      blendedBase += weight * stack.base
      blendedAdd += weight * stack.add
      blendedCarve += weight * stack.carve
    }

    return DensityStack(blendedBase, blendedAdd, blendedCarve)
  }

}
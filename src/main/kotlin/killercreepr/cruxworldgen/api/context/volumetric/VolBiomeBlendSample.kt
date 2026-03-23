package killercreepr.cruxworldgen.api.context.volumetric

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.biome.volumetric.VolumetricBiome
import killercreepr.cruxworldgen.api.biome.volumetric.WeightedVolBiome
import killercreepr.cruxworldgen.api.util.Curve
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01

data class VolBiomeBlendSample(
  val weighted: List<WeightedVolBiome>,
  val strength: Double
) {
  companion object{
    fun interpolateVolBlend(
      c000: VolBiomeBlendSample,
      c100: VolBiomeBlendSample,
      c010: VolBiomeBlendSample,
      c110: VolBiomeBlendSample,
      c001: VolBiomeBlendSample,
      c101: VolBiomeBlendSample,
      c011: VolBiomeBlendSample,
      c111: VolBiomeBlendSample,
      txRaw: Double,
      tyRaw: Double,
      tzRaw: Double
    ): VolBiomeBlendSample {
      val tx = smoothstep01(txRaw)
      val ty = smoothstep01(tyRaw)
      val tz = smoothstep01(tzRaw)

      val allBiomes = LinkedHashSet<VolumetricBiome>()
      listOf(c000, c100, c010, c110, c001, c101, c011, c111).forEach { s ->
        for ((biome, _) in s.weighted) allBiomes.add(biome)
      }

      val out = ArrayList<WeightedVolBiome>(allBiomes.size)
      var sum = 0.0

      for (biome in allBiomes) {
        val w = Curve.trilerp(
          c000.weightOf(biome), c100.weightOf(biome), c010.weightOf(biome), c110.weightOf(biome),
          c001.weightOf(biome), c101.weightOf(biome), c011.weightOf(biome), c111.weightOf(biome),
          tx, ty, tz
        )

        if (w > 1e-4) {
          out += WeightedVolBiome(biome, w)
          sum += w
        }
      }

      val strength = Curve.trilerp(
        c000.strength, c100.strength, c010.strength, c110.strength,
        c001.strength, c101.strength, c011.strength, c111.strength,
        tx, ty, tz
      ).coerceIn(0.0, 1.0)

      if (sum <= 1e-8 || strength <= 1e-8) {
        return VolBiomeBlendSample(emptyList(), 0.0)
      }

      val normalized = out.map { it.copy(weight = it.weight / sum) }
      return VolBiomeBlendSample(normalized, strength)
    }
  }

  fun isEmpty() = weighted.isEmpty()

  fun dominantWeighted(): WeightedVolBiome = weighted.maxBy { it.weight }

  fun dominantWeight(): Double = weighted.maxBy { it.weight }.weight
  fun dominant(): VolumetricBiome = weighted.maxBy { it.weight }.biome

  fun weightOf(biome : Biome) : Double{
    for (volBiome in weighted) {
      if(volBiome.biome == biome) return volBiome.weight
    }
    return 0.0
  }
}
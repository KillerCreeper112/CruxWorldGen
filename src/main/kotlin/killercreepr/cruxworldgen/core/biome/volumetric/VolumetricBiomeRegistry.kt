package killercreepr.cruxworldgen.core.biome.volumetric

import killercreepr.cruxworldgen.api.biome.volumetric.VolumetricBiome
import killercreepr.cruxworldgen.api.biome.volumetric.WeightedVolBiome
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.volumetric.VolBiomeBlendSample
import killercreepr.cruxworldgen.api.context.volumetric.VolumeEnv
import killercreepr.cruxworldgen.api.signal.SignalWriter

class VolumetricBiomeRegistry(
  val biomes: List<VolumetricBiome>
) {
  fun sample(
    ctx: GenerateContext,
    worldX: Int, y: Int, worldZ: Int,
    env: VolumeEnv,
    signals: SignalWriter
  ): VolBiomeBlendSample {
    var sum = 0.0
    val tmp = ArrayList<WeightedVolBiome>(biomes.size)

    for (b in biomes) {
      val s = b.suitability(ctx, worldX, y, worldZ, env, signals).coerceIn(0.0, 1.0)
      if (s <= 1e-6) continue
      tmp.add(WeightedVolBiome(b, s))
      sum += s
    }

    if (tmp.isEmpty() || sum <= 1e-9) return VolBiomeBlendSample(emptyList())

    // Normalize
    return VolBiomeBlendSample(tmp.map { it.copy(weight = it.weight / sum) })
  }
}
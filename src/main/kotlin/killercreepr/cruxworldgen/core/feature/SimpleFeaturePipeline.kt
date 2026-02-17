package killercreepr.cruxworldgen.core.feature

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample

class SimpleFeaturePipeline(
  val globalFeatures: List<PlacedFeature<*>>
) : FeaturePipeline {

  override fun runForChunk(
    ctx: GenerateContext,
    chunkX: Int,
    chunkZ: Int,
    biomeBlendSampler: (Int, Int) -> BiomeBlendSample
  ) {
    val rng = java.util.Random((ctx.worldContext.seed xor (chunkX.toLong() * 341873128712L) xor (chunkZ.toLong() * 132897987541L)))

    val positions = ArrayList<BlockPos>(128)

    applyFeatures(ctx, rng, chunkX, chunkZ, globalFeatures, positions)

    sampleChunkBlend(ctx, chunkX, chunkZ, biomeBlendSampler).forEach { (biome, weight) ->
      if(weight < 0.25) return@forEach
      applyFeatures(ctx, rng, chunkX, chunkZ, biome.features, positions)
    }
  }

  fun applyFeatures(
    ctx: GenerateContext,
    rng : java.util.Random,
    chunkX: Int,
    chunkZ: Int,
    features : Collection<PlacedFeature<*>>,
    positions : MutableList<BlockPos>
  ) {
    if(features.isEmpty()) return
    for (pf in features) {
      positions.clear()

      // run modifiers to create attempt centers
      for (m in pf.modifiers) {
        m.emitPositions(ctx, rng, chunkX, chunkZ, positions)
      }

      // place feature at each position
      @Suppress("UNCHECKED_CAST")
      val feature = pf.feature as Feature<Any?>
      val cfg = pf.cfg

      for (pos in positions) {
        feature.place(ctx, rng, pos, cfg)
      }
    }
  }

  fun sampleChunkBlend(ctx: GenerateContext, chunkX: Int, chunkZ: Int, biomeBlendSampler: (Int, Int) -> BiomeBlendSample, steps: Int = 2)
  : Map<Biome, Double>{
    val chunkWidth = ctx.chunkContext.width
    val chunkDepth = ctx.chunkContext.depth
    val startX = chunkX * chunkWidth
    val startZ = chunkZ * chunkDepth

    val acc = HashMap<Biome, Double>(16)
    var samples = 0

    for (sx in 0 until steps) for (sz in 0 until steps) {
      val px = startX + (sx * (chunkWidth - 1)) / (steps - 1)
      val pz = startZ + (sz * (chunkDepth - 1)) / (steps - 1)

      val blend = biomeBlendSampler(px, pz)
      for (b in blend.weightedBiomes) {
        acc[b.biome] = (acc[b.biome] ?: 0.0) + b.weight
      }
      samples++
    }

    // normalize so weights sum to 1
    val inv = 1.0 / samples
    var sum = 0.0
    for ((b, w) in acc) {
      val nw = w * inv
      acc[b] = nw
      sum += nw
    }
    if (sum > 0.0) {
      for ((b, w) in acc) acc[b] = w / sum
    }
    return acc
  }
}

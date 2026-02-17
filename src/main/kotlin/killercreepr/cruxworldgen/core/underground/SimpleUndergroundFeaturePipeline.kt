package killercreepr.cruxworldgen.core.underground

import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample

class SimpleUndergroundFeaturePipeline(
  val placed: List<PlacedFeature<*>>
) : UndergroundFeaturePipeline {

  override fun runForChunk(
    ctx: GenerateContext,
    chunkX: Int,
    chunkZ: Int,
    biomeBlendSampler: (Int, Int) -> BiomeBlendSample
  ) {
    val rng = java.util.Random((ctx.worldContext.seed xor (chunkX.toLong() * 341873128712L) xor (chunkZ.toLong() * 132897987541L)))

    val positions = ArrayList<BlockPos>(128)

    for (pf in placed) {
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
}

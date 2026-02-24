package killercreepr.cruxworldgen.core.feature

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample

class SimpleFeaturePipeline(
  val globalFeatures: List<PlacedFeature<*>>
) : FeaturePipeline {

  override fun runForChunk(
    region: LimitedRegion,
    chunkX: Int,
    chunkZ: Int,
    biomeBlendSampler: (Int, Int) -> BiomeBlendSample,
    volumetricBiomeSampler: ((Int, Int, Int) -> Biome)?
  ) {
    val ctx = region.ctx
    val rng = java.util.Random((ctx.worldContext.seed xor (chunkX.toLong() * 341873128712L) xor (chunkZ.toLong() * 132897987541L)))

    val positions = ArrayList<BlockPos>(128)

    applyFeatures(region, rng, chunkX, chunkZ, globalFeatures, positions)

    sampleChunkBlend(ctx, chunkX, chunkZ, biomeBlendSampler).forEach { (biome, weight) ->
      if(weight < 0.25) return@forEach
      applyFeatures(region, rng, chunkX, chunkZ, biome.features, positions)
    }

    if (volumetricBiomeSampler != null && false) {
      val candidateBiomes = collectCandidateBiomesForChunk3D(region, chunkX, chunkZ, volumetricBiomeSampler)

      for (biome in candidateBiomes) {
        val volumetricFeatures = biome.features
        applyFeatures3D(region, rng, chunkX, chunkZ, volumetricFeatures, positions, volumetricBiomeSampler, biome)
      }
    }
  }

  fun collectCandidateBiomesForChunk3D(
    region: LimitedRegion,
    chunkX: Int,
    chunkZ: Int,
    dominantBiomeAt: (Int, Int, Int) -> Biome
  ): Set<Biome> {
    val out = LinkedHashSet<Biome>()
    val cell = 4//todo don't hardcode this
    val startX = chunkX * region.ctx.chunkContext.width
    val startZ = chunkZ * region.ctx.chunkContext.depth
    val minY = region.ctx.chunkContext.minHeight
    val maxY = region.ctx.chunkContext.maxHeight - 1

    var x = startX + cell / 2
    while (x < startX + region.ctx.chunkContext.width) {
      var z = startZ + cell / 2
      while (z < startZ + region.ctx.chunkContext.depth) {
        var y = minY + cell / 2
        while (y <= maxY) {
          out += dominantBiomeAt(x, y, z)
          y += cell
        }
        z += cell
      }
      x += cell
    }
    return out
  }

  fun applyFeatures3D(
    region: LimitedRegion,
    rng: java.util.Random,
    chunkX: Int,
    chunkZ: Int,
    features: Collection<PlacedFeature<*>>,
    positions: MutableList<BlockPos>,
    dominantBiomeAt: (Int, Int, Int) -> Biome,
    targetBiome: Biome
  ) {
    if (features.isEmpty()) return

    for (placed in features) {
      positions.clear()

      for (modifier in placed.modifiers) {
        modifier.emitPositions(region, rng, chunkX, chunkZ, positions)
      }

      @Suppress("UNCHECKED_CAST")
      val feature = placed.feature as Feature<Any?>
      val cfg = placed.cfg

      for (pos in positions) {
        // Gate by 3D biome at the attempt position
        val biomeAtPos = dominantBiomeAt(pos.x, pos.y, pos.z)
        if (biomeAtPos !== targetBiome) continue

        feature.place(region, rng, pos, cfg)
      }
    }
  }

  fun applyFeatures(
    region: LimitedRegion,
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
        m.emitPositions(region, rng, chunkX, chunkZ, positions)
      }

      // place feature at each position
      @Suppress("UNCHECKED_CAST")
      val feature = pf.feature as Feature<Any?>
      val cfg = pf.cfg

      for (pos in positions) {
        feature.place(region, rng, pos, cfg)
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

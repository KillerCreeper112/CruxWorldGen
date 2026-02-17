package killercreepr.cruxworldgen.core.feature

import killercreepr.cruxworldgen.api.context.GenerateContext

interface PlacementModifier {
  fun emitPositions(
    ctx: GenerateContext,
    rng: java.util.Random,
    chunkX: Int,
    chunkZ: Int,
    out: MutableList<BlockPos>
  )
}

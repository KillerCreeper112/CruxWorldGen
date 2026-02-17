package killercreepr.cruxworldgen.core.feature

import killercreepr.cruxworldgen.api.context.LimitedRegion

interface PlacementModifier {
  fun emitPositions(
    region: LimitedRegion,
    rng: java.util.Random,
    chunkX: Int,
    chunkZ: Int,
    out: MutableList<BlockPos>
  )
}

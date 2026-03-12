package killercreepr.cruxworldgen.api.feature

import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.core.feature.BlockPos
import java.util.*

interface Feature<Cfg> {
  fun place(region: LimitedRegion, rng : Random, origin: BlockPos, cfg: Cfg)
}
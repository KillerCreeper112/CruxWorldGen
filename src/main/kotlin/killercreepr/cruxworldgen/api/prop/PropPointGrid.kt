package killercreepr.cruxworldgen.api.prop

import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.decor.PropPoint

interface PropPointGrid{
  fun pointsForChunk(ctx: GenerateContext, chunkX: Int, chunkZ: Int): List<PropPoint>
}
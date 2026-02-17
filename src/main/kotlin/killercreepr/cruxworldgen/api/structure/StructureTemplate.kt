package killercreepr.cruxworldgen.api.structure

import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.LimitedRegion

interface StructureTemplate {
  val bounds: Aabb
  fun placeIntoChunk(
    region: LimitedRegion,
    inst: StructureInstance,
    processors: List<BlockProcessor> = emptyList()
  )
}
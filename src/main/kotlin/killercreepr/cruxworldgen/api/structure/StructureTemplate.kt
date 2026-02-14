package killercreepr.cruxworldgen.api.structure

import killercreepr.cruxworldgen.api.context.GenerateContext

interface StructureTemplate {
  val bounds: Aabb
  fun placeIntoChunk(
    ctx: GenerateContext,
    inst: StructureInstance,
    processors: List<BlockProcessor> = emptyList()
  )
}
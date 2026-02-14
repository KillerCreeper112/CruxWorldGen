package killercreepr.cruxworldgen.api.structure

import killercreepr.cruxworldgen.api.context.GenerateContext

interface StructurePlacementRule {
  fun pickInstancesForChunk(ctx: GenerateContext, chunkX: Int, chunkZ: Int): List<StructureInstance>
}
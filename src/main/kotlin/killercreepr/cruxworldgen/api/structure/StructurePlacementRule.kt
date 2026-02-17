package killercreepr.cruxworldgen.api.structure

import killercreepr.cruxworldgen.api.context.LimitedRegion

interface StructurePlacementRule {
  fun pickInstancesForChunk(region: LimitedRegion, chunkX: Int, chunkZ: Int): List<StructureInstance>
}
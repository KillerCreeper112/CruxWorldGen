package killercreepr.cruxworldgen.api.structure

import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.core.structure.SimpleStructurePipeline

interface StructurePipeline{
  companion object{
    fun structurePipeline(registry : StructureRegistry) : StructurePipeline = SimpleStructurePipeline(registry)
  }

  val registry: StructureRegistry
  fun runForChunk(region: LimitedRegion, chunkX: Int, chunkZ: Int)
}
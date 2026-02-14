package killercreepr.cruxworldgen.api.structure

import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.core.structure.SimpleStructurePipeline

interface StructurePipeline{
  companion object{
    fun structurePipeline(registry : StructureRegistry) : StructurePipeline = SimpleStructurePipeline(registry)
  }

  val registry: StructureRegistry
  fun runForChunk(ctx: GenerateContext, chunkX: Int, chunkZ: Int)
}
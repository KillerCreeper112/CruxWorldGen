package killercreepr.cruxworldgen.api.structure

import killercreepr.cruxworldgen.api.context.GenerateContext

interface StructurePipeline{
  val registry: StructureRegistry
  fun runForChunk(ctx: GenerateContext, chunkX: Int, chunkZ: Int)
}
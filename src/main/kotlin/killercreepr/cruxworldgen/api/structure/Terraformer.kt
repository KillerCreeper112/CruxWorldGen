package killercreepr.cruxworldgen.api.structure

import killercreepr.cruxworldgen.api.context.GenerateContext

interface Terraformer {
  fun terraformChunk(ctx: GenerateContext, inst: StructureInstance, template: StructureTemplate)
}
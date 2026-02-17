package killercreepr.cruxworldgen.api.structure

import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.LimitedRegion

interface Terraformer {
  fun terraformChunk(region: LimitedRegion, inst: StructureInstance, template: StructureTemplate)
}
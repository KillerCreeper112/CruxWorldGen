package killercreepr.cruxworldgen.core.structure

import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.structure.StructurePipeline
import killercreepr.cruxworldgen.api.structure.StructureRegistry

class SimpleStructurePipeline(override val registry: StructureRegistry) : StructurePipeline {
  override fun runForChunk(
    ctx: GenerateContext,
    chunkX: Int,
    chunkZ: Int
  ) {
    for (feature in registry.features) {
      val instances = feature.placement.pickInstancesForChunk(ctx, chunkX, chunkZ)
      for (inst in instances) {
        if (feature.terraformFirst) {
          if (feature.wantsTerraform) feature.terraformer.terraformChunk(ctx, inst, feature.template)
          if (feature.wantsPlacement) feature.template.placeIntoChunk(ctx, inst, feature.processors)
        } else {
          if (feature.wantsPlacement) feature.template.placeIntoChunk(ctx, inst, feature.processors)
          if (feature.wantsTerraform) feature.terraformer.terraformChunk(ctx, inst, feature.template)
        }
      }
    }
  }
}
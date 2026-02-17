package killercreepr.cruxworldgen.core.structure

import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.structure.StructurePipeline
import killercreepr.cruxworldgen.api.structure.StructureRegistry

class SimpleStructurePipeline(override val registry: StructureRegistry) : StructurePipeline {
  override fun runForChunk(
    region: LimitedRegion,
    chunkX: Int,
    chunkZ: Int
  ) {
    for (feature in registry.features) {
      val instances = feature.placement.pickInstancesForChunk(region, chunkX, chunkZ)
      for (inst in instances) {
        if (feature.terraformFirst) {
          if (feature.wantsTerraform) feature.terraformer.terraformChunk(region, inst, feature.template)
          if (feature.wantsPlacement) feature.template.placeIntoChunk(region, inst, feature.processors)
        } else {
          if (feature.wantsPlacement) feature.template.placeIntoChunk(region, inst, feature.processors)
          if (feature.wantsTerraform) feature.terraformer.terraformChunk(region, inst, feature.template)
        }
      }
    }
  }
}
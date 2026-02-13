package killercreepr.cruxworldgen.test6.structure

import killercreepr.cruxworldgen.test6.context.GenerateContext

data class StructureInstance(
  val id: String,
  val worldX: Int,
  val worldY: Int,
  val worldZ: Int,
  val rot: Int,      // 0/90/180/270
  val seed: Long
)

interface StructurePlacementRule {
  fun pickInstancesForChunk(ctx: GenerateContext, chunkX: Int, chunkZ: Int): List<StructureInstance>
}

interface Terraformer {
  fun terraformChunk(ctx: GenerateContext, inst: StructureInstance, template: StructureTemplate)
}

interface StructureFeature {
  val id: String
  val placement: StructurePlacementRule
  val template: StructureTemplate
  val terraformer: Terraformer

  val processors: List<BlockProcessor> get() = emptyList()

  // ordering knobs
  val terraformFirst: Boolean get() = true
  val wantsTerraform: Boolean get() = true
  val wantsPlacement: Boolean get() = true
}

class StructureRegistry(
  private val features: List<StructureFeature>
) {
  fun features(): List<StructureFeature> = features
}

class StructurePipeline(
  private val registry: StructureRegistry
) {
  fun runForChunk(ctx: GenerateContext, chunkX: Int, chunkZ: Int) {
    for (feature in registry.features()) {
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

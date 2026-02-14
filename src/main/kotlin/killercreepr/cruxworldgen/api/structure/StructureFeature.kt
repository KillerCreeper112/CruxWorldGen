package killercreepr.cruxworldgen.api.structure

interface StructureFeature {
  val placement: StructurePlacementRule
  val template: StructureTemplate
  val terraformer: Terraformer

  val processors: List<BlockProcessor> get() = emptyList()

  // ordering knobs
  val terraformFirst: Boolean get() = true
  val wantsTerraform: Boolean get() = true
  val wantsPlacement: Boolean get() = true
}
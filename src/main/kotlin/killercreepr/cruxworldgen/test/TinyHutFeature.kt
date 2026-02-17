/*
package killercreepr.cruxworldgen.test

import killercreepr.cruxworldgen.api.structure.*
import killercreepr.cruxworldgen.api.structure.standard.BlockListTemplate
import killercreepr.cruxworldgen.core.structure.CellPlacementRule
import killercreepr.cruxworldgen.core.structure.NaturalPadTerraformer

class TinyHutFeature : StructureFeature {
  val id: String = "tiny_hut"

  override val template: StructureTemplate = BlockListTemplate(buildBlocks())
  override val terraformer: Terraformer = NaturalPadTerraformer()
  override val placement: StructurePlacementRule = CellPlacementRule(
    featureId = id,
    cellSizeChunks = 8,
    chancePerCell = 0.5,
    yOffset = 1,
    borderPadding = 3
  )

  private fun buildBlocks(): List<RelBlock> {
    val out = ArrayList<RelBlock>()

    // 5x5 floor at y=0
    //for (x in 0..4) for (z in 0..4) out.add(RelBlock(x, 0, z, Material.OAK_PLANKS))

    // 3-block tall walls (simple)
    */
/*for (y in 1..3) {
      for (i in 0..4) {
        out.add(RelBlock(0, y, i, Material.OAK_LOG))
        out.add(RelBlock(4, y, i, Material.OAK_LOG))
        out.add(RelBlock(i, y, 0, Material.OAK_LOG))
        out.add(RelBlock(i, y, 4, Material.OAK_LOG))
      }
    }*//*


    // doorway
    out.removeIf { it.x == 2 && (it.y == 1 || it.y == 2) && it.z == 0 }

    // roof
    //for (x in 0..4) for (z in 0..4) out.add(RelBlock(x, 4, z, Material.OAK_SLAB))

    return out
  }
}*/

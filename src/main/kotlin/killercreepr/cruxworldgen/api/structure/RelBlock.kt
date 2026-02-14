package killercreepr.cruxworldgen.api.structure

import killercreepr.cruxworldgen.api.block.BlockData

data class RelBlock(
  val x: Int,
  val y: Int,
  val z: Int,
  val mat: BlockData
)
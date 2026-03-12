package killercreepr.cruxworldgen.standard.decor

import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.context.LimitedRegion

fun interface BlockPicker {
  fun pickBlock(region: LimitedRegion, x: Int, y: Int, z: Int): BlockData?
}